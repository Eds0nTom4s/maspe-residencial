package com.restaurante.service;

import com.restaurante.dto.request.SolicitarOtpRequest;
import com.restaurante.dto.request.ValidarOtpRequest;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cliente;
import com.restaurante.notificacao.service.NotificacaoService;
import com.restaurante.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;

/**
 * Service para operações de negócio com Cliente
 * Responsável por autenticação via OTP
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository clienteRepository;
    private final NotificacaoService notificacaoService;
    
    @Value("${otp.length:4}")
    private int otpLength;

    @Value("${otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${otp.mock:true}")
    private boolean otpMock;

    /**
     * Solicita um OTP para o telefone informado
     * Cria cliente se não existir
     */
    @Transactional
    public void solicitarOtp(SolicitarOtpRequest request) {
        log.info("Solicitando OTP para telefone: {}", request.getTelefone());
        
        Cliente cliente = clienteRepository.findByTelefone(request.getTelefone())
                .orElseGet(() -> criarNovoCliente(request.getTelefone(), null));

        String otp = gerarOtp();
        cliente.setOtpCode(otp);
        cliente.setOtpExpiration(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
        
        clienteRepository.save(cliente);

        if (otpMock) {
            log.info("🔧 MOCK OTP ATIVADO: O código OTP gerado para o telefone {} é [{}] (válido por {} minutos)", 
                request.getTelefone(), otp, otpExpirationMinutes);
        } else {
            // Envia OTP via SMS (TelcoSMS)
            boolean enviado = notificacaoService.enviarOtp(request.getTelefone(), otp);
            
            if (enviado) {
                log.info("OTP enviado com sucesso para {} via TelcoSMS (válido por {} minutos)", 
                    request.getTelefone(), otpExpirationMinutes);
            } else {
                log.warn("Falha ao enviar OTP via SMS para {}, mas código foi salvo no banco para contingência", 
                    request.getTelefone());
            }
        }
    }

    /**
     * Valida o OTP informado e retorna o cliente autenticado
     */
    @Transactional
    public ClienteResponse validarOtp(ValidarOtpRequest request) {
        log.info("Validando OTP para telefone: {}", request.getTelefone());

        Cliente cliente = clienteRepository.findByTelefone(request.getTelefone())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "telefone", request.getTelefone()));

        if (!cliente.isOtpValido(request.getCodigo())) {
            throw new BusinessException("Código OTP inválido ou expirado");
        }

        // Marca telefone como verificado
        cliente.setTelefoneVerificado(true);
        cliente.setOtpCode(null);
        cliente.setOtpExpiration(null);
        clienteRepository.save(cliente);

        log.info("OTP validado com sucesso para cliente ID: {}", cliente.getId());
        return mapToResponse(cliente);
    }

    /**
     * Busca cliente por ID
     */
    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "id", id));
    }

    /**
     * Busca cliente por telefone
     */
    @Transactional(readOnly = true)
    public Cliente buscarPorTelefone(String telefone) {
        return clienteRepository.findByTelefone(telefone)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "telefone", telefone));
    }

    @Transactional
    public Cliente buscarOuCriarPorTelefone(String telefone) {
        return buscarOuCriarPorTelefone(telefone, null);
    }

    /**
     * Retorna o cliente pelo telefone, criando-o automaticamente se não existir.
     * Se o cliente existir mas o nome for nulo e um nome novo for fornecido, atualiza-o.
     */
    @Transactional
    public Cliente buscarOuCriarPorTelefone(String telefone, String nome) {
        return clienteRepository.findByTelefone(telefone)
                .map(cliente -> {
                    // Se o cliente existe mas está sem nome, e recebemos um nome, atualizamos
                    if ((cliente.getNome() == null || cliente.getNome().isBlank()) && nome != null && !nome.isBlank()) {
                        log.info("Atualizando nome para cliente existente {}: {}", telefone, nome);
                        cliente.setNome(nome.trim());
                        return clienteRepository.save(cliente);
                    }
                    return cliente;
                })
                .orElseGet(() -> {
                    log.info("Cliente não encontrado para telefone {}. Criando automaticamente com nome '{}'.", telefone, nome);
                    return criarNovoCliente(telefone, nome);
                });
    }

    /**
     * Cria um novo cliente com nome opcional
     */
    private Cliente criarNovoCliente(String telefone, String nome) {
        Cliente cliente = Cliente.builder()
                .telefone(telefone)
                .nome(nome != null && !nome.isBlank() ? nome.trim() : null)
                .telefoneVerificado(false)
                .ativo(true)
                .build();
        
        return clienteRepository.save(cliente);
    }

    /**
     * Gera um código OTP aleatório
     */
    private String gerarOtp() {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();
        
        for (int i = 0; i < otpLength; i++) {
            otp.append(random.nextInt(10));
        }
        
        return otp.toString();
    }

    /**
     * Lista todos os clientes com paginação
     */
    @Transactional(readOnly = true)
    public Page<ClienteResponse> listarTodos(Pageable pageable) {
        return clienteRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Actualiza dados do cliente (nome)
     */
    @Transactional
    public ClienteResponse atualizar(Long id, String nome) {
        Cliente cliente = buscarPorId(id);
        if (nome != null && !nome.isBlank()) {
            cliente.setNome(nome.trim());
        }
        return mapToResponse(clienteRepository.save(cliente));
    }

    /**
     * Desactiva cliente (soft delete)
     */
    @Transactional
    public void desativar(Long id) {
        Cliente cliente = buscarPorId(id);
        if (!cliente.getAtivo()) {
            throw new BusinessException("Cliente já está inativo");
        }
        cliente.setAtivo(false);
        clienteRepository.save(cliente);
        log.info("Cliente {} desativado com sucesso", id);
    }

    /**
     * Mapeia Cliente para ClienteResponse
     */
    private ClienteResponse mapToResponse(Cliente cliente) {
        ClienteResponse response = new ClienteResponse();
        response.setId(cliente.getId());
        response.setTelefone(cliente.getTelefone());
        response.setNome(cliente.getNome());
        response.setTelefoneVerificado(cliente.getTelefoneVerificado());
        response.setAtivo(cliente.getAtivo());
        response.setCreatedAt(cliente.getCreatedAt());
        return response;
    }
}
