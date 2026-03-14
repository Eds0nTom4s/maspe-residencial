package com.restaurante.service;

import com.restaurante.dto.request.CriarMesaRequest;
import com.restaurante.dto.response.MesaResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service para operações sobre Mesa.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>CRUD administrativo de mesas físicas (ADMIN).</li>
 *   <li>Derivação do status DISPONÍVEL/OCUPADA em tempo real.</li>
 * </ul>
 *
 * <p>NÃO possui lógica de abertura/fechamento de sessões.
 * Para isso, use {@link SessaoConsumoService}.
 */
@Service
public class MesaService {

    private static final Logger log = LoggerFactory.getLogger(MesaService.class);

    private final MesaRepository mesaRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    public MesaService(MesaRepository mesaRepository,
                       SessaoConsumoRepository sessaoConsumoRepository,
                       UnidadeAtendimentoRepository unidadeAtendimentoRepository) {
        this.mesaRepository = mesaRepository;
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.unidadeAtendimentoRepository = unidadeAtendimentoRepository;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Operações administrativas (ADMIN)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Cria uma nova mesa física.
     * Operação exclusiva do ADMIN — a mesa é um recurso permanente.
     */
    @Transactional
    public MesaResponse criar(CriarMesaRequest request) {
        log.info("Criando mesa: referencia='{}', unidadeAtendimento={}",
                request.getReferencia(), request.getUnidadeAtendimentoId());

        UnidadeAtendimento unidadeAtendimento = unidadeAtendimentoRepository
                .findById(request.getUnidadeAtendimentoId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada"));

        Mesa mesa = Mesa.builder()
                .referencia(request.getReferencia())
                .tipo(request.getTipo() != null ? request.getTipo() : TipoUnidadeConsumo.MESA_FISICA)
                .numero(request.getNumero())
                .qrCode(request.getQrCode())
                .capacidade(request.getCapacidade())
                .ativa(true)
                .unidadeAtendimento(unidadeAtendimento)
                .build();

        Mesa mesaSalva = mesaRepository.save(mesa);
        log.info("Mesa criada: ID={}, referencia='{}'", mesaSalva.getId(), mesaSalva.getReferencia());
        return converterParaResponse(mesaSalva);
    }

    /**
     * Ativa uma mesa (torna-a disponível para receber sessões).
     */
    @Transactional
    public MesaResponse ativar(Long id) {
        Mesa mesa = buscarEntidadePorId(id);
        mesa.setAtiva(true);
        log.info("Mesa ID={} ativada", id);
        return converterParaResponse(mesaRepository.save(mesa));
    }

    /**
     * Desativa uma mesa.
     * Não é permitido desativar mesa com sessão aberta.
     */
    @Transactional
    public MesaResponse desativar(Long id) {
        Mesa mesa = buscarEntidadePorId(id);
        if (sessaoConsumoRepository.existsByMesaIdAndStatus(id, StatusSessaoConsumo.ABERTA)) {
            throw new BusinessException(
                    "Não é possível desativar mesa '" + mesa.getReferencia() + "': possui sessão aberta");
        }
        mesa.setAtiva(false);
        log.info("Mesa ID={} desativada", id);
        return converterParaResponse(mesaRepository.save(mesa));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consultas
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MesaResponse buscarPorId(Long id) {
        return converterParaResponse(buscarEntidadePorId(id));
    }

    @Transactional(readOnly = true)
    public MesaResponse buscarPorQrCode(String qrCode) {
        Mesa mesa = mesaRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada para o QR Code: " + qrCode));
        return converterParaResponse(mesa);
    }

    /**
     * Lista todas as mesas com status derivado em tempo real.
     */
    @Transactional(readOnly = true)
    public List<MesaResponse> listarTodas() {
        return mesaRepository.findAll().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MesaResponse> listarAtivas() {
        return mesaRepository.findByAtiva(true).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista mesas DISPONÍVEIS (sem sessão aberta) — status derivado.
     */
    @Transactional(readOnly = true)
    public List<MesaResponse> listarDisponiveis() {
        return mesaRepository.findDisponiveis().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista mesas OCUPADAS (com sessão aberta) — status derivado.
     */
    @Transactional(readOnly = true)
    public List<MesaResponse> listarOcupadas() {
        return mesaRepository.findOcupadas().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MesaResponse> listarPorUnidadeAtendimento(Long unidadeAtendimentoId) {
        return mesaRepository.findByUnidadeAtendimentoId(unidadeAtendimentoId).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ──────────────────────────────────────────────────────────────────────────

    public Mesa buscarEntidadePorId(Long id) {
        return mesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada: " + id));
    }

    /**
     * Converte Mesa para MesaResponse, calculando o status derivado em tempo real.
     *
     * <p>Status NUNCA vem do banco — é sempre recalculado via EXISTS na SessaoConsumo.
     */
    public MesaResponse converterParaResponse(Mesa mesa) {
        Optional<SessaoConsumo> sessaoAtiva = sessaoConsumoRepository
                .findByMesaIdAndStatus(mesa.getId(), StatusSessaoConsumo.ABERTA);

        String statusDerived = sessaoAtiva.isPresent() ? "OCUPADA" : "DISPONIVEL";

        return MesaResponse.builder()
                .id(mesa.getId())
                .referencia(mesa.getReferencia())
                .numero(mesa.getNumero())
                .qrCode(mesa.getQrCode())
                .capacidade(mesa.getCapacidade())
                .ativa(mesa.getAtiva())
                .tipo(mesa.getTipo())
                .unidadeAtendimentoId(mesa.getUnidadeAtendimento().getId())
                .unidadeAtendimentoNome(mesa.getUnidadeAtendimento().getNome())
                .status(statusDerived)
                .sessaoAtivaId(sessaoAtiva.map(SessaoConsumo::getId).orElse(null))
                .createdAt(mesa.getCreatedAt())
                .build();
    }
}
