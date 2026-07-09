package com.restaurante.financeiro.snapshot.evidence.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.snapshot.dto.SnapshotFinanceiroExportResponse;
import com.restaurante.financeiro.snapshot.dto.SnapshotVerificacaoIntegridadeResponse;
import com.restaurante.financeiro.snapshot.evidence.EvidenceBundleProperties;
import com.restaurante.financeiro.snapshot.evidence.dto.SnapshotFinanceiroEvidenceBundleResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleChainResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleDetailResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleIntegrityResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleListItemResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundlePersistResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleRetentionResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleVerificationResponse;
import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundle;
import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundleAccessLog;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleAccessLogRepository;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleRepository;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.EvidenceBundleAccessType;
import com.restaurante.model.enums.EvidenceBundleStatus;
import com.restaurante.model.enums.EvidenceBundleType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.financeiro.snapshot.evidence.service.EvidenceBundleIntegrityService.EvidenceBundleChain;
import com.restaurante.financeiro.snapshot.evidence.service.EvidenceBundleIntegrityService.EvidenceBundleIntegrity;
import com.restaurante.financeiro.snapshot.evidence.service.EvidenceBundleIntegrityService.EvidenceBundleVerification;
import com.restaurante.financeiro.snapshot.service.SnapshotFinanceiroExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TurnoEvidenceBundleService {

    private final TenantGuard tenantGuard;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final TurnoEvidenceBundleRepository bundleRepository;
    private final TurnoEvidenceBundleAccessLogRepository accessLogRepository;
    private final SnapshotFinanceiroEvidenceBundleService evidenceBundleService;
    private final SnapshotFinanceiroExportService snapshotExportService;
    private final EvidenceBundleIntegrityService integrityService;
    private final EvidenceBundleProperties props;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public EvidenceBundlePersistResponse criarPersistido(Long turnoId, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE
        );
        TenantContext ctx = TenantContextHolder.require();

        // Lock no turno para serializar geração do sequenceNumber
        TurnoOperacional turno = turnoOperacionalRepository.findForUpdateById(turnoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (turno.getTenant() == null || !turno.getTenant().getId().equals(ctx.tenantId())) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        if (turno.getStatus() != TurnoOperacionalStatus.FECHADO) {
            throw new ConflictException("Turno não está FECHADO para persistir evidence bundle.");
        }

        // Exigir snapshot íntegro no momento de persistir (não persistimos evidência sobre snapshot inválido)
        SnapshotFinanceiroExportResponse snap = snapshotExportService.exportar(turnoId, ip, userAgent);
        SnapshotVerificacaoIntegridadeResponse ver = snap.getVerificacao();
        if (ver == null || !ver.isValido()) {
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.EVIDENCE_BUNDLE_INTEGRIDADE_INVALIDA,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "Bloqueado: snapshot financeiro com integridade inválida para persistir evidence bundle",
                    Map.of(
                            "hashValido", ver != null ? ver.getHashValido() : null,
                            "assinaturaValida", ver != null ? ver.getAssinaturaValida() : null
                    ),
                    ip,
                    userAgent
            );
            throw new ConflictException("Snapshot financeiro com integridade inválida; evidence bundle não pode ser persistido.");
        }

        SnapshotFinanceiroEvidenceBundleResponse bundle = evidenceBundleService.gerar(turnoId, ip, userAgent);
        JsonNode bundleNode = objectMapper.valueToTree(bundle);

        // Buscar último bundle do turno (com lock de linha, se existir)
        TurnoEvidenceBundle previous = bundleRepository.findLastForUpdate(ctx.tenantId(), turnoId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);

        int seq = previous != null ? previous.getSequenceNumber() + 1 : 1;
        String prevHash = previous != null ? previous.getBundleHash() : null;

        EvidenceBundleIntegrity integ = integrityService.calcularIntegridadeBundle(
                props.getHashAlgorithm(),
                props.getCanonicalizationVersion(),
                bundleNode
        );
        EvidenceBundleChain chain = integrityService.calcularChain(
                props.getHashAlgorithm(),
                props.getCanonicalizationVersion(),
                ctx.tenantId(),
                turnoId,
                seq,
                prevHash,
                integ.bundleHash
        );

        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime retentionUntil = props.isRetentionEnabled()
                ? generatedAt.plusDays(props.getDefaultRetentionDays())
                : null;

        User actor = ctx.userId() != null ? userRepository.findById(ctx.userId()).orElse(null) : null;

        TurnoEvidenceBundle entity = new TurnoEvidenceBundle();
        entity.setTenant(turno.getTenant());
        entity.setTurno(turno);
        entity.setInstituicao(turno.getInstituicao());
        entity.setUnidadeAtendimento(turno.getUnidadeAtendimento());
        entity.setBundleVersion(bundle.getBundleVersion());
        entity.setBundleType(EvidenceBundleType.FINANCEIRO_TURNO_SNAPSHOT_EVIDENCE);
        entity.setStatus(EvidenceBundleStatus.ACTIVE);
        entity.setSequenceNumber(seq);
        entity.setGeneratedAt(generatedAt);
        entity.setGeneratedByUser(actor);
        entity.setGeneratedByActorType("USER");
        entity.setSourceEndpoint("POST /tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles");
        entity.setCanonicalizationVersion(integ.canonicalizationVersion);
        entity.setHashAlgorithm(integ.hashAlgorithm);
        entity.setBundleHash(integ.bundleHash);
        entity.setSignatureAlgorithm(integ.signatureAlgorithm);
        entity.setBundleSignature(integ.bundleSignature);
        entity.setSignatureKeyId(integ.signatureKeyId);
        entity.setSignatureGeneratedAt(integ.signatureGeneratedAt);
        entity.setPreviousBundle(previous);
        entity.setPreviousBundleHash(prevHash);
        entity.setChainHash(chain.chainHash);
        entity.setChainSignature(chain.chainSignature);
        entity.setChainSignatureKeyId(chain.chainSignatureKeyId);
        entity.setChainSignatureGeneratedAt(chain.chainSignatureGeneratedAt);
        entity.setRetentionUntil(retentionUntil);
        entity.setWormLocked(props.isWormLockEnabled());
        entity.setBundleJson(writeJson(bundleNode));
        entity.setMetadataJson(writeJson(objectMapper.valueToTree(Map.of(
                "maxEvents", props.getMaxEvents(),
                "snapshotVerificacaoValido", true
        ))));

        TurnoEvidenceBundle saved = bundleRepository.save(entity);
        registrarAccess(saved, ctx, EvidenceBundleAccessType.CREATED, ip, userAgent, "OK", null);

        operationalEventLogService.logTurnoEvent(
                OperationalEventType.EVIDENCE_BUNDLE_PERSISTIDO,
                turno,
                resolveOrigemFromRoles(ctx),
                "Evidence bundle persistido (append-only)",
                new HashMap<>() {{
                    put("bundleId", saved.getId());
                    put("sequenceNumber", saved.getSequenceNumber());
                    put("bundleHash", saved.getBundleHash());
                    put("chainHash", saved.getChainHash());
                    put("signatureKeyId", saved.getSignatureKeyId());
                }},
                ip,
                userAgent
        );

        if (retentionUntil != null) {
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.EVIDENCE_BUNDLE_RETENTION_APLICADA,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "Retenção aplicada ao evidence bundle",
                    Map.of(
                            "bundleId", saved.getId(),
                            "retentionUntil", retentionUntil
                    ),
                    ip,
                    userAgent
            );
        }
        if (saved.isWormLocked()) {
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.EVIDENCE_BUNDLE_WORM_LOCK_APLICADO,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "WORM lógico aplicado ao evidence bundle",
                    Map.of(
                            "bundleId", saved.getId(),
                            "wormLocked", true
                    ),
                    ip,
                    userAgent
            );
        }

        return toPersistResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public Page<EvidenceBundleListItemResponse> listar(Long turnoId, Pageable pageable) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        TenantContext ctx = TenantContextHolder.require();

        // valida turno no tenant
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        Page<TurnoEvidenceBundle> page = bundleRepository.findByTenantIdAndTurnoIdOrderBySequenceNumberDesc(ctx.tenantId(), turno.getId(), pageable);
        return page.map(this::toListItem);
    }

    @Transactional
    public EvidenceBundleDetailResponse detalhar(Long turnoId, Long bundleId, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        TenantContext ctx = TenantContextHolder.require();

        TurnoEvidenceBundle bundle = bundleRepository.findByIdAndTenantIdAndTurnoId(bundleId, ctx.tenantId(), turnoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        JsonNode bundleNode = readJson(bundle.getBundleJson());
        EvidenceBundleDetailResponse out = new EvidenceBundleDetailResponse();
        out.setBundleId(bundle.getId());
        out.setTenantId(ctx.tenantId());
        out.setTurnoId(turnoId);
        out.setSequenceNumber(bundle.getSequenceNumber());
        out.setBundleVersion(bundle.getBundleVersion());
        out.setBundleType(bundle.getBundleType().name());
        out.setStatus(bundle.getStatus().name());
        out.setGeneratedAt(bundle.getGeneratedAt());
        out.setGeneratedByUserId(bundle.getGeneratedByUser() != null ? bundle.getGeneratedByUser().getId() : null);
        out.setBundle(bundleNode);
        out.setIntegridade(toIntegrity(bundle, null));
        out.setCadeiaCustodia(toChain(bundle, null, null));
        out.setRetencao(toRetention(bundle));
        out.setVerification(verificarInterno(bundle, bundleNode));

        registrarAccess(bundle, ctx, EvidenceBundleAccessType.EXPORTED, ip, userAgent, out.getVerification().isValido() ? "OK" : "INVALID", null);
        operationalEventLogService.logTurnoEvent(
                OperationalEventType.EVIDENCE_BUNDLE_EXPORTADO,
                bundle.getTurno(),
                resolveOrigemFromRoles(ctx),
                "Evidence bundle persistido exportado (leitura)",
                Map.of("bundleId", bundle.getId(), "sequenceNumber", bundle.getSequenceNumber(), "valido", out.getVerification().isValido()),
                ip,
                userAgent
        );
        return out;
    }

    @Transactional
    public EvidenceBundleVerificationResponse verificarPersistido(Long turnoId, Long bundleId, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        TenantContext ctx = TenantContextHolder.require();

        TurnoEvidenceBundle bundle = bundleRepository.findByIdAndTenantIdAndTurnoId(bundleId, ctx.tenantId(), turnoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        JsonNode bundleNode = readJson(bundle.getBundleJson());
        EvidenceBundleVerificationResponse v = verificarInterno(bundle, bundleNode);

        registrarAccess(bundle, ctx, EvidenceBundleAccessType.VERIFIED, ip, userAgent, v.isValido() ? "OK" : "INVALID", null);
        if (v.isValido()) {
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.EVIDENCE_BUNDLE_VERIFICADO,
                    bundle.getTurno(),
                    resolveOrigemFromRoles(ctx),
                    "Evidence bundle verificado (OK)",
                    Map.of(
                            "bundleId", bundle.getId(),
                            "sequenceNumber", bundle.getSequenceNumber(),
                            "valido", true
                    ),
                    ip,
                    userAgent
            );
        } else {
            OperationalEventType evType = (v.getFailureReason() != null
                    && (v.getFailureReason().startsWith("CHAIN_") || v.getFailureReason().startsWith("PREVIOUS_LINK_")))
                    ? OperationalEventType.EVIDENCE_BUNDLE_CHAIN_INVALIDA
                    : OperationalEventType.EVIDENCE_BUNDLE_INTEGRIDADE_INVALIDA;
            operationalEventLogService.logTurnoEvent(
                    evType,
                    bundle.getTurno(),
                    resolveOrigemFromRoles(ctx),
                    "Evidence bundle com integridade/cadeia inválida",
                    Map.of(
                            "bundleId", bundle.getId(),
                            "sequenceNumber", bundle.getSequenceNumber(),
                            "valido", false,
                            "failureReason", v.getFailureReason()
                    ),
                    ip,
                    userAgent
            );
        }
        return v;
    }

    private EvidenceBundleVerificationResponse verificarInterno(TurnoEvidenceBundle bundle, JsonNode bundleNode) {
        String expectedPrevHash = bundle.getPreviousBundle() != null ? bundle.getPreviousBundle().getBundleHash() : null;
        String expectedChainHash = integrityService.calcularChainHash(
                bundle.getHashAlgorithm(),
                bundle.getCanonicalizationVersion(),
                bundle.getTenant().getId(),
                bundle.getTurno().getId(),
                bundle.getSequenceNumber(),
                bundle.getPreviousBundleHash(),
                bundle.getBundleHash()
        );

        EvidenceBundleVerification v = integrityService.verificar(
                bundle.getHashAlgorithm(),
                bundleNode,
                bundle.getBundleHash(),
                bundle.getBundleSignature(),
                bundle.getSignatureKeyId(),
                bundle.getChainHash(),
                bundle.getChainSignature(),
                bundle.getChainSignatureKeyId(),
                expectedChainHash,
                expectedPrevHash,
                bundle.getPreviousBundleHash()
        );

        EvidenceBundleVerificationResponse out = new EvidenceBundleVerificationResponse();
        out.setValido(v.valido);
        out.setBundleHashValido(v.bundleHashValido);
        out.setBundleSignatureValida(v.bundleSignatureValida);
        out.setChainHashValido(v.chainHashValido);
        out.setChainSignatureValida(v.chainSignatureValida);
        out.setPreviousLinkValido(v.previousLinkValido);
        out.setFailureReason(v.failureReason);
        out.setVerificadoEm(v.verificadoEm);
        out.setBundleHashPersistido(v.bundleHashPersistido);
        out.setBundleHashRecalculado(v.bundleHashRecalculado);
        out.setSignatureKeyFound(v.bundleSignatureKeyFound);
        out.setSignatureKeyStatus(v.bundleSignatureKeyStatus);
        out.setSignatureFailureReason(v.bundleSignatureFailureReason);
        out.setChainSignatureKeyFound(v.chainSignatureKeyFound);
        out.setChainSignatureKeyStatus(v.chainSignatureKeyStatus);
        out.setChainSignatureFailureReason(v.chainSignatureFailureReason);
        return out;
    }

    private EvidenceBundlePersistResponse toPersistResponse(TurnoEvidenceBundle saved, EvidenceBundleVerificationResponse verification) {
        EvidenceBundlePersistResponse resp = new EvidenceBundlePersistResponse();
        resp.setBundleId(saved.getId());
        resp.setTenantId(saved.getTenant() != null ? saved.getTenant().getId() : null);
        resp.setTurnoId(saved.getTurno() != null ? saved.getTurno().getId() : null);
        resp.setSequenceNumber(saved.getSequenceNumber());
        resp.setBundleVersion(saved.getBundleVersion());
        resp.setBundleType(saved.getBundleType().name());
        resp.setStatus(saved.getStatus().name());
        resp.setGeneratedAt(saved.getGeneratedAt());
        resp.setIntegridade(toIntegrity(saved, verification));
        resp.setCadeiaCustodia(toChain(saved, verification, null));
        resp.setRetencao(toRetention(saved));
        resp.setVerification(verification);
        return resp;
    }

    private EvidenceBundleListItemResponse toListItem(TurnoEvidenceBundle b) {
        EvidenceBundleListItemResponse r = new EvidenceBundleListItemResponse();
        r.setBundleId(b.getId());
        r.setSequenceNumber(b.getSequenceNumber());
        r.setBundleVersion(b.getBundleVersion());
        r.setBundleType(b.getBundleType() != null ? b.getBundleType().name() : null);
        r.setStatus(b.getStatus() != null ? b.getStatus().name() : null);
        r.setGeneratedAt(b.getGeneratedAt());
        r.setGeneratedByUserId(b.getGeneratedByUser() != null ? b.getGeneratedByUser().getId() : null);
        r.setBundleHash(b.getBundleHash());
        r.setSignatureKeyId(b.getSignatureKeyId());
        r.setChainHash(b.getChainHash());
        r.setRetentionUntil(b.getRetentionUntil());
        r.setWormLocked(b.isWormLocked());
        return r;
    }

    private EvidenceBundleIntegrityResponse toIntegrity(TurnoEvidenceBundle b, EvidenceBundleVerificationResponse verification) {
        EvidenceBundleIntegrityResponse r = new EvidenceBundleIntegrityResponse();
        r.setCanonicalizationVersion(b.getCanonicalizationVersion());
        r.setHashAlgorithm(b.getHashAlgorithm());
        r.setBundleHash(b.getBundleHash());
        r.setSignatureAlgorithm(b.getSignatureAlgorithm());
        r.setBundleSignature(b.getBundleSignature());
        r.setSignatureKeyId(b.getSignatureKeyId());
        r.setSignatureGeneratedAt(b.getSignatureGeneratedAt());
        r.setSignatureKeyStatus(verification != null ? verification.getSignatureKeyStatus() : null);
        return r;
    }

    private EvidenceBundleChainResponse toChain(TurnoEvidenceBundle b, EvidenceBundleVerificationResponse verification, String chainKeyStatus) {
        EvidenceBundleChainResponse r = new EvidenceBundleChainResponse();
        r.setSequenceNumber(b.getSequenceNumber());
        r.setPreviousBundleId(b.getPreviousBundle() != null ? b.getPreviousBundle().getId() : null);
        r.setPreviousBundleHash(b.getPreviousBundleHash());
        r.setChainHash(b.getChainHash());
        r.setChainSignature(b.getChainSignature());
        r.setChainSignatureKeyId(b.getChainSignatureKeyId());
        r.setChainSignatureGeneratedAt(b.getChainSignatureGeneratedAt());
        r.setChainSignatureKeyStatus(verification != null ? verification.getChainSignatureKeyStatus() : chainKeyStatus);
        return r;
    }

    private EvidenceBundleRetentionResponse toRetention(TurnoEvidenceBundle b) {
        EvidenceBundleRetentionResponse r = new EvidenceBundleRetentionResponse();
        r.setRetentionPolicy(props.isRetentionEnabled() ? ("DEFAULT_" + props.getDefaultRetentionDays() + "_DAYS") : "DISABLED");
        r.setRetentionUntil(b.getRetentionUntil());
        r.setWormLocked(b.isWormLocked());
        r.setDeleteAllowed(false);
        r.setDeleteReason("WORM lógico: não existe delete físico nesta fase.");
        return r;
    }

    private void registrarAccess(TurnoEvidenceBundle bundle,
                                 TenantContext ctx,
                                 EvidenceBundleAccessType type,
                                 String ip,
                                 String userAgent,
                                 String verificationResult,
                                 Map<String, Object> metadata) {
        TurnoEvidenceBundleAccessLog log = new TurnoEvidenceBundleAccessLog();
        log.setTenant(bundle.getTenant());
        log.setBundle(bundle);
        log.setTurno(bundle.getTurno());
        log.setAccessedAt(LocalDateTime.now());
        log.setAccessedByUser(ctx.userId() != null ? userRepository.findById(ctx.userId()).orElse(null) : null);
        log.setActorType("USER");
        log.setAccessType(type);
        log.setSourceIp(ip);
        log.setUserAgent(userAgent);
        log.setVerificationResult(verificationResult);
        if (metadata != null && !metadata.isEmpty()) {
            log.setMetadataJson(writeJson(objectMapper.valueToTree(metadata)));
        }
        accessLogRepository.save(log);
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar JSON do evidence bundle.", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler JSON do evidence bundle persistido.", e);
        }
    }

    private OperationalOrigem resolveOrigemFromRoles(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) return OperationalOrigem.SYSTEM;
        if (ctx.roles().contains("TENANT_ADMIN") || ctx.roles().contains("TENANT_OWNER")) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains("TENANT_FINANCE")) return OperationalOrigem.TENANT_FINANCE;
        if (ctx.roles().contains("TENANT_CASHIER")) return OperationalOrigem.TENANT_CASHIER;
        return OperationalOrigem.TENANT_OPERATOR;
    }
}
