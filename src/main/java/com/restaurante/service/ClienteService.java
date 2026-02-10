package com.restaurante.service;

import com.restaurante.dto.request.SolicitarOtpRequest;
import com.restaurante.dto.request.ValidarOtpRequest;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cliente;
import com.restaurante.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Service para operações de negócio com Cliente
 * Responsável por autenticação via OTP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRATION_MINUTES = 5;

    /**
     * Solicita um OTP para o telefone informado
     * Cria cliente se não existir
     */
    @Transactional
    public void solicitarOtp(SolicitarOtpRequest request) {
        log.info("Solicitando OTP para telefone: {}", request.getTelefone());
        
        Cliente cliente = clienteRepository.findByTelefone(request.getTelefone())
                .orElseGet(() -> criarNovoCliente(request.getTelefone()));

        String otp = gerarOtp();
        cliente.setOtpCode(otp);
        cliente.setOtpExpiration(LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES));
        
        clienteRepository.save(cliente);

        // TODO: Integrar com serviço de SMS/WhatsApp para enviar OTP
        log.info("OTP gerado: {} (válido por {} minutos)", otp, OTP_EXPIRATION_MINUTES);
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

    /**
     * Cria um novo cliente
     */
    private Cliente criarNovoCliente(String telefone) {
        Cliente cliente = Cliente.builder()
                .telefone(telefone)
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
        
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        
        return otp.toString();
    }

    /**
     * Mapeia Cliente para ClienteResponse
     */
    private ClienteResponse mapToResponse(Cliente cliente) {
        return ClienteResponse.builder()
                .id(cliente.getId())
                .telefone(cliente.getTelefone())
                .nome(cliente.getNome())
                .telefoneVerificado(cliente.getTelefoneVerificado())
                .ativo(cliente.getAtivo())
                .createdAt(cliente.getCreatedAt())
                .build();
    }
}
