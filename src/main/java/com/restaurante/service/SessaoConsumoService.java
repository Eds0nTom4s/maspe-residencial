package com.restaurante.service;

import com.restaurante.dto.request.AbrirSessaoRequest;
import com.restaurante.dto.response.SessaoConsumoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Atendente;
import com.restaurante.model.entity.Cliente;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.AtendenteRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsável pelo ciclo de vida da SessaoConsumo.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Abrir nova sessão numa mesa disponível (ATENDENTE/ADMIN/GERENTE).</li>
 *   <li>Encerrar sessão (ATENDENTE/GERENTE/ADMIN).</li>
 *   <li>Sinalizar sessão para aguardar pagamento.</li>
 *   <li>Consultar histórico de sessões por mesa.</li>
 * </ul>
 *
 * <p>Regras fundamentais:
 * <ul>
 *   <li>Uma mesa só pode ter UMA SessaoConsumo ABERTA por vez.</li>
 *   <li>Fechar uma sessão NÃO altera a mesa — o status da mesa é derivado.</li>
 *   <li>Cada ocupação SEMPRE gera uma nova SessaoConsumo — nunca reutiliza.</li>
 *   <li>O histórico de sessões é PRESERVADO integralmente.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessaoConsumoService {

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final MesaRepository mesaRepository;
    private final ClienteService clienteService;
    private final AtendenteRepository atendenteRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // Operações de ciclo de vida
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Abre uma nova sessão de consumo em uma mesa disponível.
     *
     * <p>Validações:
     * <ul>
     *   <li>Mesa deve existir e estar ativa.</li>
     *   <li>Mesa não pode ter sessão ABERTA — garante unicidade.</li>
     *   <li>No fluxo identificado: telefone obrigatório; cliente não pode ter outra sessão aberta.</li>
     *   <li>No fluxo anônimo: QR Code da mesa é o token do fundo de consumo.</li>
     * </ul>
     */
    @Transactional
    public SessaoConsumoResponse abrir(AbrirSessaoRequest request) {
        log.info("Abrindo sessão de consumo: mesaId={}, modoAnonimo={}",
                request.getMesaId(), request.isModoAnonimo());

        Mesa mesa = mesaRepository.findById(request.getMesaId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada: " + request.getMesaId()));

        if (!mesa.getAtiva()) {
            throw new BusinessException("Mesa '" + mesa.getReferencia() + "' está inativa");
        }

        // Invariante: apenas UMA sessão ABERTA por mesa
        if (sessaoConsumoRepository.existsByMesaIdAndStatus(mesa.getId(), StatusSessaoConsumo.ABERTA)) {
            throw new BusinessException(
                    "Mesa '" + mesa.getReferencia() + "' já possui sessão aberta. " +
                    "Encerre a sessão atual antes de abrir uma nova.");
        }

        SessaoConsumo.SessaoConsumoBuilder builder = SessaoConsumo.builder()
                .mesa(mesa)
                .modoAnonimo(request.isModoAnonimo());

        if (!request.isModoAnonimo()) {
            // ── Fluxo identificado ──────────────────────────────────────────
            if (request.getTelefoneCliente() == null || request.getTelefoneCliente().isBlank()) {
                throw new BusinessException("Telefone do cliente é obrigatório no fluxo identificado");
            }
            Cliente cliente = clienteService.buscarOuCriarPorTelefone(request.getTelefoneCliente());

            // Impede cliente com sessão aberta em outra mesa
            sessaoConsumoRepository.findSessaoAbertaByCliente(cliente.getId())
                    .ifPresent(sessaoExistente -> {
                        throw new BusinessException(
                                "Cliente '" + cliente.getNome() + "' já possui sessão aberta na mesa '"
                                + sessaoExistente.getMesa().getReferencia() + "'");
                    });

            builder.cliente(cliente);
            log.info("Sessão identificada: clienteId={}", cliente.getId());

        } else {
            // ── Fluxo anônimo ───────────────────────────────────────────────
            // QR Code da mesa é o token único do portador
            String token = mesa.getQrCode() != null ? mesa.getQrCode() : "MESA-" + mesa.getId();
            builder.qrCodePortador(token);
            log.info("Sessão anônima: token portador='{}'", token);
        }

        // Atendente responsável pela abertura (opcional)
        if (request.getAtendenteId() != null) {
            Atendente atendente = atendenteRepository.findById(request.getAtendenteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Atendente não encontrado: " + request.getAtendenteId()));
            builder.aberturaPor(atendente);
        }

        SessaoConsumo sessao = builder.build();
        SessaoConsumo sessaoSalva = sessaoConsumoRepository.save(sessao);

        log.info("Sessão aberta com sucesso: ID={}, mesa='{}', status={}",
                sessaoSalva.getId(), mesa.getReferencia(), sessaoSalva.getStatus());

        return converterParaResponse(sessaoSalva);
    }

    /**
     * Encerra a sessão de consumo.
     *
     * <p>Ao encerrar, a mesa fica DISPONÍVEL automaticamente (status derivado).
     * O histórico da sessão é preservado integralmente.
     */
    @Transactional
    public SessaoConsumoResponse fechar(Long id) {
        log.info("Encerrando sessão de consumo ID={}", id);

        SessaoConsumo sessao = buscarEntidadePorId(id);

        if (sessao.getStatus() == StatusSessaoConsumo.ENCERRADA) {
            throw new BusinessException("Sessão ID=" + id + " já está encerrada");
        }

        sessao.encerrar();
        SessaoConsumo sessaoSalva = sessaoConsumoRepository.save(sessao);

        log.info("Sessão encerrada: ID={}, mesa='{}' agora DISPONÍVEL",
                id, sessao.getMesa().getReferencia());

        return converterParaResponse(sessaoSalva);
    }

    /**
     * Transiciona sessão para AGUARDANDO_PAGAMENTO.
     * A mesa permanece OCUPADA até o encerramento definitivo.
     */
    @Transactional
    public SessaoConsumoResponse aguardarPagamento(Long id) {
        log.info("Transicionando sessão ID={} para AGUARDANDO_PAGAMENTO", id);

        SessaoConsumo sessao = buscarEntidadePorId(id);

        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) {
            throw new BusinessException(
                    "Sessão deve estar ABERTA para aguardar pagamento. Status atual: " + sessao.getStatus());
        }

        sessao.aguardarPagamento();
        return converterParaResponse(sessaoConsumoRepository.save(sessao));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consultas
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SessaoConsumoResponse buscarPorId(Long id) {
        return converterParaResponse(buscarEntidadePorId(id));
    }

    @Transactional(readOnly = true)
    public SessaoConsumoResponse buscarSessaoAbertaDaMesa(Long mesaId) {
        SessaoConsumo sessao = sessaoConsumoRepository
                .findByMesaIdAndStatus(mesaId, StatusSessaoConsumo.ABERTA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhuma sessão ABERTA encontrada para a mesa ID=" + mesaId));
        return converterParaResponse(sessao);
    }

    @Transactional(readOnly = true)
    public List<SessaoConsumoResponse> listarAbertas() {
        return sessaoConsumoRepository.findByStatus(StatusSessaoConsumo.ABERTA).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SessaoConsumoResponse> listarPorMesa(Long mesaId) {
        return sessaoConsumoRepository.findByMesaIdOrderByAbertaEmDesc(mesaId).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ──────────────────────────────────────────────────────────────────────────

    public SessaoConsumo buscarEntidadePorId(Long id) {
        return sessaoConsumoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão de consumo não encontrada: " + id));
    }

    public SessaoConsumoResponse converterParaResponse(SessaoConsumo sessao) {
        BigDecimal totalConsumo = sessao.getPedidos().stream()
                .map(p -> p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SessaoConsumoResponse.builder()
                .id(sessao.getId())
                .mesaId(sessao.getMesa().getId())
                .referenciaMesa(sessao.getMesa().getReferencia())
                .clienteId(sessao.getCliente() != null ? sessao.getCliente().getId() : null)
                .nomeCliente(sessao.getCliente() != null ? sessao.getCliente().getNome() : null)
                .telefoneCliente(sessao.getCliente() != null ? sessao.getCliente().getTelefone() : null)
                .aberturaPorId(sessao.getAberturaPor() != null ? sessao.getAberturaPor().getId() : null)
                .nomeAtendente(sessao.getAberturaPor() != null ? sessao.getAberturaPor().getNome() : null)
                .abertaEm(sessao.getAbertaEm())
                .fechadaEm(sessao.getFechadaEm())
                .status(sessao.getStatus())
                .modoAnonimo(sessao.getModoAnonimo())
                .qrCodePortador(sessao.getQrCodePortador())
                .totalConsumo(totalConsumo)
                .build();
    }
}
