package com.restaurante.service;

import com.restaurante.dto.request.CriarUnidadeConsumoRequest;
import com.restaurante.dto.response.UnidadeConsumoResponse;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.dto.response.PedidoResumoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Atendente;
import com.restaurante.model.entity.Cliente;
import com.restaurante.model.entity.UnidadeDeConsumo;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.StatusUnidadeConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import com.restaurante.repository.AtendenteRepository;
import com.restaurante.repository.UnidadeDeConsumoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service para operações de negócio com UnidadeDeConsumo
 * Regra importante: UnidadeDeConsumo SEMPRE deve estar associada a um cliente
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnidadeDeConsumoService {

    private final UnidadeDeConsumoRepository unidadeDeConsumoRepository;
    private final ClienteService clienteService;
    private final AtendenteRepository atendenteRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    /**
     * Cria uma nova unidade de consumo associada a um cliente
     * Utilizado quando cliente escaneia QR Code ou atendente cria manualmente
     */
    @Transactional
    public UnidadeConsumoResponse criar(CriarUnidadeConsumoRequest request) {
        log.info("Criando nova unidade de consumo: {} para telefone: {}", request.getReferencia(), request.getTelefoneCliente());

        // Busca ou cria cliente
        Cliente cliente = clienteService.buscarPorTelefone(request.getTelefoneCliente());

        // Verifica se cliente já possui unidade ativa
        unidadeDeConsumoRepository.findUnidadeAtivaByCliente(cliente.getId())
                .ifPresent(unidade -> {
                    throw new BusinessException("Cliente já possui uma unidade ativa (" + unidade.getReferencia() + ")");
                });

        // Busca unidade de atendimento
        UnidadeAtendimento unidadeAtendimento = unidadeAtendimentoRepository.findById(request.getUnidadeAtendimentoId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada"));

        UnidadeDeConsumo unidade = UnidadeDeConsumo.builder()
                .referencia(request.getReferencia())
                .tipo(request.getTipo() != null ? request.getTipo() : TipoUnidadeConsumo.MESA_FISICA)
                .numero(request.getNumero())
                .qrCode(request.getQrCode())
                .capacidade(request.getCapacidade())
                .status(StatusUnidadeConsumo.OCUPADA)
                .cliente(cliente)
                .unidadeAtendimento(unidadeAtendimento)
                .build();

        // Se foi criada por atendente
        if (request.getAtendenteId() != null) {
            Atendente atendente = atendenteRepository.findById(request.getAtendenteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Atendente não encontrado"));
            unidade.setAtendente(atendente);
        }

        UnidadeDeConsumo unidadeSalva = unidadeDeConsumoRepository.save(unidade);
        log.info("Unidade de consumo criada com sucesso: {}", unidadeSalva.getId());

        return converterParaResponse(unidadeSalva);
    }

    /**
     * Busca unidade de consumo por ID
     */
    @Transactional(readOnly = true)
    public UnidadeConsumoResponse buscarPorId(Long id) {
        UnidadeDeConsumo unidade = unidadeDeConsumoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de consumo não encontrada"));
        return converterParaResponse(unidade);
    }

    /**
     * Busca unidade de consumo por QR Code
     */
    @Transactional(readOnly = true)
    public UnidadeConsumoResponse buscarPorQrCode(String qrCode) {
        UnidadeDeConsumo unidade = unidadeDeConsumoRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de consumo não encontrada"));
        return converterParaResponse(unidade);
    }

    /**
     * Lista todas as unidades de consumo
     */
    @Transactional(readOnly = true)
    public List<UnidadeConsumoResponse> listarTodas() {
        return unidadeDeConsumoRepository.findAll().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista unidades por status
     */
    @Transactional(readOnly = true)
    public List<UnidadeConsumoResponse> listarPorStatus(StatusUnidadeConsumo status) {
        return unidadeDeConsumoRepository.findByStatus(status).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista unidades disponíveis de um tipo específico
     */
    @Transactional(readOnly = true)
    public List<UnidadeConsumoResponse> listarDisponiveisPorTipo(TipoUnidadeConsumo tipo) {
        return unidadeDeConsumoRepository.findByStatusAndTipo(StatusUnidadeConsumo.DISPONIVEL, tipo).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista unidades de uma Unidade de Atendimento
     */
    @Transactional(readOnly = true)
    public List<UnidadeConsumoResponse> listarPorUnidadeAtendimento(Long unidadeAtendimentoId) {
        return unidadeDeConsumoRepository.findByUnidadeAtendimentoId(unidadeAtendimentoId).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    /**
     * Fecha uma unidade de consumo
     */
    @Transactional
    public UnidadeConsumoResponse fechar(Long id) {
        log.info("Fechando unidade de consumo ID: {}", id);

        UnidadeDeConsumo unidade = unidadeDeConsumoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de consumo não encontrada"));

        if (unidade.getStatus() == StatusUnidadeConsumo.FINALIZADA) {
            throw new BusinessException("Unidade de consumo já está finalizada");
        }

        // Verificação de pagamento removida - será gerenciada pelo módulo financeiro

        unidade.fechar();
        UnidadeDeConsumo unidadeSalva = unidadeDeConsumoRepository.save(unidade);

        log.info("Unidade de consumo fechada com sucesso: {}", id);
        return converterParaResponse(unidadeSalva);
    }

    /**
     * Atualiza status da unidade
     */
    @Transactional
    public UnidadeConsumoResponse atualizarStatus(Long id) {
        log.info("Atualizando status da unidade de consumo ID: {}", id);

        UnidadeDeConsumo unidade = unidadeDeConsumoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de consumo não encontrada"));

        unidade.atualizarStatus();
        UnidadeDeConsumo unidadeSalva = unidadeDeConsumoRepository.save(unidade);

        return converterParaResponse(unidadeSalva);
    }

    /**
     * Converte entity para DTO response
     */
    private UnidadeConsumoResponse converterParaResponse(UnidadeDeConsumo unidade) {
        ClienteResponse clienteResponse = ClienteResponse.builder()
                .id(unidade.getCliente().getId())
                .telefone(unidade.getCliente().getTelefone())
                .nome(unidade.getCliente().getNome())
                .build();

        List<PedidoResumoResponse> pedidosResponse = unidade.getPedidos().stream()
                .map(pedido -> PedidoResumoResponse.builder()
                        .id(pedido.getId())
                        .numero(pedido.getNumero())
                        .status(pedido.getStatus())
                        .total(pedido.getTotal())
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalUnidade = unidade.calcularTotal();

        return UnidadeConsumoResponse.builder()
                .id(unidade.getId())
                .referencia(unidade.getReferencia())
                .tipo(unidade.getTipo())
                .numero(unidade.getNumero())
                .qrCode(unidade.getQrCode())
                .status(unidade.getStatus())
                .capacidade(unidade.getCapacidade())
                .cliente(clienteResponse)
                .pedidos(pedidosResponse)
                .total(totalUnidade)
                .abertaEm(unidade.getAbertaEm())
                .fechadaEm(unidade.getFechadaEm())
                .build();
    }
}
