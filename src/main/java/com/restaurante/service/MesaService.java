package com.restaurante.service;

import com.restaurante.dto.request.CriarMesaRequest;
import com.restaurante.dto.response.MesaResponse;
import com.restaurante.dto.response.TenantMesaResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final InstituicaoRepository instituicaoRepository;
    private final TenantGuard tenantGuard;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final OperationalEventLogService operationalEventLogService;
    private static final Pattern CODIGO_NUMERICO_MESA = Pattern.compile(".*(?:MESA[-\\s]?)(\\d+)$");

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public MesaService(MesaRepository mesaRepository,
                       SessaoConsumoRepository sessaoConsumoRepository,
                       UnidadeAtendimentoRepository unidadeAtendimentoRepository,
                       InstituicaoRepository instituicaoRepository,
                       TenantGuard tenantGuard,
                       QrCodeOperacionalRepository qrCodeOperacionalRepository,
                       OperationalEventLogService operationalEventLogService) {
        this.mesaRepository = mesaRepository;
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.unidadeAtendimentoRepository = unidadeAtendimentoRepository;
        this.instituicaoRepository = instituicaoRepository;
        this.tenantGuard = tenantGuard;
        this.qrCodeOperacionalRepository = qrCodeOperacionalRepository;
        this.operationalEventLogService = operationalEventLogService;
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
        Instituicao instituicao = unidadeAtendimento.getInstituicao() != null
                ? unidadeAtendimento.getInstituicao()
                : buscarInstituicaoAtiva();
        if (instituicao.getTenant() == null) {
            throw new BusinessException("Instituição inválida para criação de mesa (tenant ausente).");
        }

        Mesa mesa = Mesa.builder()
                .referencia(request.getReferencia())
                .tipo(request.getTipo() != null ? request.getTipo() : TipoUnidadeConsumo.MESA_FISICA)
                .numero(request.getNumero())
                .qrCode(request.getQrCode())
                .capacidade(request.getCapacidade())
                .ativa(true)
                .unidadeAtendimento(unidadeAtendimento)
                .tenant(instituicao.getTenant())
                .instituicao(instituicao)
                .build();

        Mesa mesaSalva = mesaRepository.save(mesa);
        log.info("Mesa criada: ID={}, referencia='{}'", mesaSalva.getId(), mesaSalva.getReferencia());
        return converterParaResponse(mesaSalva);
    }

    @Transactional
    public TenantMesaResponse criarTenantAware(CriarMesaRequest request) {
        Long tenantId = requireTenantId("criação de mesa");
        UnidadeAtendimento unidadeAtendimento = unidadeAtendimentoRepository
                .findByIdAndTenantId(request.getUnidadeAtendimentoId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada"));
        Instituicao instituicao = unidadeAtendimento.getInstituicao();
        if (instituicao == null || instituicao.getTenant() == null || !tenantId.equals(instituicao.getTenant().getId())) {
            throw new BusinessException("Unidade de atendimento inválida para o tenant atual.");
        }

        Mesa mesa = Mesa.builder()
                .referencia(request.getReferencia())
                .tipo(request.getTipo() != null ? request.getTipo() : TipoUnidadeConsumo.MESA_FISICA)
                .numero(request.getNumero())
                .qrCode(request.getQrCode())
                .capacidade(request.getCapacidade())
                .ativa(true)
                .unidadeAtendimento(unidadeAtendimento)
                .tenant(instituicao.getTenant())
                .instituicao(instituicao)
                .build();

        Mesa saved = mesaRepository.save(mesa);
        logTenantMesaEvent(tenantId, OperationalEventType.MESA_CRIADA, saved, "Mesa criada");
        return converterParaTenantResponse(saved);
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

    /**
     * Renomear uma mesa física.
     */
    @Transactional
    public MesaResponse renomear(Long id, String novaReferencia) {
        Mesa mesa = buscarEntidadePorId(id);
        mesa.setReferencia(novaReferencia);
        log.info("Mesa ID={} renomeada para '{}'", id, novaReferencia);
        return converterParaResponse(mesaRepository.save(mesa));
    }

    @Transactional
    public TenantMesaResponse atualizarTenantAware(Long id, CriarMesaRequest request) {
        Long tenantId = requireTenantId("atualização de mesa");
        Mesa mesa = buscarEntidadePorIdDoTenant(id, tenantId);
        UnidadeAtendimento unidadeAtendimento = unidadeAtendimentoRepository
                .findByIdAndTenantId(request.getUnidadeAtendimentoId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de atendimento não encontrada"));
        mesa.setReferencia(request.getReferencia());
        mesa.setNumero(request.getNumero());
        mesa.setQrCode(request.getQrCode());
        mesa.setCapacidade(request.getCapacidade());
        mesa.setTipo(request.getTipo() != null ? request.getTipo() : TipoUnidadeConsumo.MESA_FISICA);
        mesa.setUnidadeAtendimento(unidadeAtendimento);
        mesa.setInstituicao(unidadeAtendimento.getInstituicao());
        Mesa saved = mesaRepository.save(mesa);
        logTenantMesaEvent(tenantId, OperationalEventType.MESA_ATUALIZADA, saved, "Mesa atualizada");
        return converterParaTenantResponse(saved);
    }

    @Transactional
    public TenantMesaResponse alterarAtivaTenantAware(Long id, boolean ativa) {
        Long tenantId = requireTenantId("alteração de estado de mesa");
        Mesa mesa = buscarEntidadePorIdDoTenant(id, tenantId);
        if (!ativa && sessaoConsumoRepository.existsByMesaIdAndStatus(id, StatusSessaoConsumo.ABERTA)) {
            throw new BusinessException("Não é possível desativar mesa com sessão aberta.");
        }
        mesa.setAtiva(ativa);
        Mesa saved = mesaRepository.save(mesa);
        logTenantMesaEvent(
                tenantId,
                ativa ? OperationalEventType.MESA_ATIVADA : OperationalEventType.MESA_DESATIVADA,
                saved,
                ativa ? "Mesa ativada" : "Mesa desativada"
        );
        return converterParaTenantResponse(saved);
    }

    @Transactional
    public void desativarTenantAware(Long id) {
        alterarAtivaTenantAware(id, false);
    }

    /**
     * Remover uma mesa física.
     * Não é permitido remover mesa com sessão aberta.
     */
    @Transactional
    public void remover(Long id) {
        Mesa mesa = buscarEntidadePorId(id);
        if (sessaoConsumoRepository.existsByMesaIdAndStatus(id, StatusSessaoConsumo.ABERTA)) {
            throw new BusinessException(
                    "Não é possível remover mesa '" + mesa.getReferencia() + "': possui sessão aberta");
        }
        mesaRepository.delete(mesa);
        log.info("Mesa ID={} removida", id);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consultas
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MesaResponse buscarPorId(Long id) {
        return converterParaResponse(buscarEntidadePorId(id));
    }

    @Transactional(readOnly = true)
    public TenantMesaResponse buscarPorIdTenantAware(Long id) {
        Long tenantId = requireTenantId("leitura de mesa");
        return converterParaTenantResponse(buscarEntidadePorIdDoTenant(id, tenantId));
    }

    @Transactional(readOnly = true)
    public MesaResponse buscarPorQrCode(String qrCode) {
        Mesa mesa = mesaRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada para o QR Code: " + qrCode));
        return converterParaResponse(mesa);
    }

    @Transactional(readOnly = true)
    public MesaResponse buscarPorCodigoPublico(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessException("Código da mesa é obrigatório");
        }

        String codigoNormalizado = codigo.trim().toUpperCase();
        Mesa mesa = mesaRepository.findByQrCode(codigoNormalizado)
                .or(() -> mesaRepository.findByReferenciaIgnoreCase(codigo.trim()))
                .or(() -> buscarPorNumeroNoCodigo(codigoNormalizado))
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada para o código: " + codigo));

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
    public List<TenantMesaResponse> listarTodasDoTenant() {
        Long tenantId = requireTenantId("listagem de mesas");
        return mesaRepository.findByTenantId(tenantId).stream()
                .map(this::converterParaTenantResponse)
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

    @Transactional(readOnly = true)
    public List<Map<String, String>> listarTipos() {
        return Arrays.stream(TipoUnidadeConsumo.values())
                .map(tipo -> Map.of(
                        "codigo", tipo.name(),
                        "descricao", tipo.getDescricao()))
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ──────────────────────────────────────────────────────────────────────────

    public Mesa buscarEntidadePorId(Long id) {
        return mesaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada: " + id));
    }

    public Mesa buscarEntidadePorIdDoTenant(Long id, Long tenantId) {
        return mesaRepository.findByIdAndTenantId(id, tenantId)
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
                .instituicaoId(resolverInstituicao(mesa).getId())
                .instituicaoSigla(resolverInstituicao(mesa).getSigla())
                .status(statusDerived)
                .sessaoAtivaId(sessaoAtiva.map(SessaoConsumo::getId).orElse(null))
                .createdAt(mesa.getCreatedAt())
                .build();
    }

    public TenantMesaResponse converterParaTenantResponse(Mesa mesa) {
        boolean ocupada = sessaoConsumoRepository.existsByMesaIdAndStatus(mesa.getId(), StatusSessaoConsumo.ABERTA);
        QrCodeOperacional qr = qrCodeOperacionalRepository
                .findFirstByMesaIdAndTenantIdAndAtivoTrueAndRevogadoFalse(mesa.getId(), mesa.getTenant().getId())
                .orElse(null);
        return TenantMesaResponse.builder()
                .id(mesa.getId())
                .instituicaoId(resolverInstituicao(mesa).getId())
                .unidadeAtendimentoId(mesa.getUnidadeAtendimento().getId())
                .numero(mesa.getNumero())
                .referencia(mesa.getReferencia())
                .ativa(Boolean.TRUE.equals(mesa.getAtiva()))
                .ocupada(ocupada)
                .possuiQr(qr != null)
                .qrCodeId(qr != null ? qr.getId() : null)
                .qrToken(qr != null ? qr.getToken() : null)
                .qrUrlPublica(qr != null ? buildPublicQrUrl(qr.getToken()) : null)
                .build();
    }

    private Instituicao buscarInstituicaoAtiva() {
        return instituicaoRepository.findFirstByAtivaTrue()
                .orElseThrow(() -> new BusinessException("Nenhuma instituição ativa configurada"));
    }

    private Instituicao resolverInstituicao(Mesa mesa) {
        if (mesa.getInstituicao() != null) {
            return mesa.getInstituicao();
        }
        if (mesa.getUnidadeAtendimento() != null && mesa.getUnidadeAtendimento().getInstituicao() != null) {
            return mesa.getUnidadeAtendimento().getInstituicao();
        }
        return buscarInstituicaoAtiva();
    }

    private Optional<Mesa> buscarPorNumeroNoCodigo(String codigoNormalizado) {
        Matcher matcher = CODIGO_NUMERICO_MESA.matcher(codigoNormalizado);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            Integer numero = Integer.valueOf(matcher.group(1));
            return mesaRepository.findFirstByNumero(numero);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Long requireTenantId(String operacao) {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para " + operacao + ".");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return tenantId;
    }

    private void logTenantMesaEvent(Long tenantId, OperationalEventType eventType, Mesa mesa, String descricao) {
        operationalEventLogService.logGenericForTenant(
                tenantId,
                eventType,
                OperationalEntityType.MESA,
                mesa.getId(),
                OperationalOrigem.TENANT_ADMIN,
                descricao,
                Map.of("referencia", mesa.getReferencia(), "ativa", Boolean.TRUE.equals(mesa.getAtiva())),
                null,
                null
        );
    }

    private String buildPublicQrUrl(String token) {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "http://localhost:8080";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/q/" + token;
    }
}
