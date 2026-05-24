package com.restaurante.fiscal.official.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.official.client.OfficialFiscalClient;
import com.restaurante.fiscal.official.config.OfficialFiscalProperties;
import com.restaurante.fiscal.official.dto.OfficialFiscalDocumentPayload;
import com.restaurante.fiscal.official.repository.OfficialFiscalSubmissionAttemptRepository;
import com.restaurante.fiscal.official.repository.OfficialFiscalSubmissionRepository;
import com.restaurante.fiscal.official.repository.TenantOfficialFiscalProfileRepository;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.FiscalSigningProfile;
import com.restaurante.model.entity.OfficialFiscalSubmission;
import com.restaurante.model.entity.OfficialFiscalSubmissionAttempt;
import com.restaurante.model.entity.TenantOfficialFiscalProfile;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.OfficialFiscalEnvironment;
import com.restaurante.model.enums.OfficialFiscalSubmissionMode;
import com.restaurante.model.enums.OfficialFiscalSubmissionStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.restaurante.repository.TenantRepository;
import com.restaurante.fiscal.official.repository.FiscalSigningProfileRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfficialFiscalSubmissionService {

    private final OfficialFiscalProperties props;
    private final TenantOfficialFiscalProfileRepository profileRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentLineRepository fiscalDocumentLineRepository;
    private final OfficialFiscalSubmissionRepository submissionRepository;
    private final OfficialFiscalSubmissionAttemptRepository attemptRepository;
    private final OfficialFiscalDocumentMapper mapper;
    private final OfficialFiscalPayloadCanonicalService canonicalService;
    private final OfficialFiscalSigningService signingService;
    private final OfficialFiscalClient client;
    private final OperationalEventLogService operationalEventLogService;
    private final TenantRepository tenantRepository;
    private final FiscalSigningProfileRepository signingProfileRepository;

    @Transactional(readOnly = true)
    public TenantOfficialFiscalProfile getProfile(Long tenantId) {
        return profileRepository.findByTenantId(tenantId).orElse(null);
    }

    @Transactional
    public TenantOfficialFiscalProfile upsertProfile(Long tenantId,
                                                     com.restaurante.dto.request.UpsertTenantOfficialFiscalProfileRequest incoming) {
        if (tenantId == null) throw new BusinessException("tenantId é obrigatório.");
        if (incoming == null) throw new BusinessException("profile é obrigatório.");

        TenantOfficialFiscalProfile current = profileRepository.findByTenantId(tenantId).orElse(null);
        if (current == null) {
            current = new TenantOfficialFiscalProfile();
            current.setTenant(requireTenantForId(tenantId));
        }
        boolean created = current.getId() == null;

        if (incoming.getStatus() != null) current.setStatus(incoming.getStatus());
        if (incoming.getCountryCode() != null) current.setCountryCode(incoming.getCountryCode());
        if (incoming.getAuthority() != null) current.setAuthority(incoming.getAuthority());
        if (incoming.getOfficialEnabled() != null) current.setOfficialEnabled(incoming.getOfficialEnabled());
        if (incoming.getEnvironment() != null) current.setEnvironment(incoming.getEnvironment());
        if (incoming.getSubmissionMode() != null) current.setSubmissionMode(incoming.getSubmissionMode());
        current.setTaxpayerNumber(incoming.getTaxpayerNumber());
        current.setSoftwareCertificateId(incoming.getSoftwareCertificateId());
        current.setSoftwareName(incoming.getSoftwareName());
        current.setSoftwareVersion(incoming.getSoftwareVersion());
        current.setProducerRegistrationId(incoming.getProducerRegistrationId());
        current.setPublicKeyId(incoming.getPublicKeyId());
        current.setTaxpayerKeyId(incoming.getTaxpayerKeyId());
        current.setCallbackUrl(incoming.getCallbackUrl());

        if (incoming.getSigningProfileId() != null) {
            FiscalSigningProfile sp = signingProfileRepository.findById(incoming.getSigningProfileId()).orElseThrow(() -> new BusinessException("SigningProfile não encontrado."));
            current.setSigningProfile(sp);
        }

        validateProfileSafety(current);

        TenantOfficialFiscalProfile saved = profileRepository.save(current);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                created ? OperationalEventType.OFFICIAL_FISCAL_PROFILE_CREATED : OperationalEventType.OFFICIAL_FISCAL_PROFILE_UPDATED,
                OperationalEntityType.OFFICIAL_FISCAL_PROFILE,
                saved.getId(),
                OperationalOrigem.SYSTEM,
                "Perfil oficial fiscal atualizado",
                Map.of(
                        "tenantId", tenantId,
                        "officialEnabled", saved.isOfficialEnabled(),
                        "environment", saved.getEnvironment().name(),
                        "submissionMode", saved.getSubmissionMode().name()
                ),
                null,
                null
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<OfficialFiscalSubmission> listSubmissions(Long tenantId, OfficialFiscalSubmissionStatus status, Pageable pageable) {
        if (tenantId == null) throw new BusinessException("tenantId é obrigatório.");
        return submissionRepository.listByTenant(tenantId, status, pageable);
    }

    @Transactional(readOnly = true)
    public OfficialFiscalSubmission getSubmission(Long tenantId, Long submissionId) {
        OfficialFiscalSubmission s = submissionRepository.findById(submissionId).orElse(null);
        if (s == null) return null;
        if (s.getTenant() == null || !s.getTenant().getId().equals(tenantId)) return null;
        return s;
    }

    @Transactional
    public OfficialFiscalSubmission createForDocument(Long tenantId, Long fiscalDocumentId) {
        if (!props.isEnabled()) throw new BusinessException("Official fiscal integration está desativada (consuma.fiscal.official.enabled=false).");
        TenantOfficialFiscalProfile profile = requireProfile(tenantId);
        validateProfileSafety(profile);
        if (!profile.isOfficialEnabled() || profile.getSubmissionMode() == OfficialFiscalSubmissionMode.DISABLED) {
            throw new BusinessException("Submissão oficial desativada para o tenant.");
        }

        FiscalDocument d = requireIssuedFiscalDocument(tenantId, fiscalDocumentId);
        String idem = idempotencyKey(tenantId, d.getId());

        OfficialFiscalSubmission existing = submissionRepository.findByTenantIdAndFiscalDocumentId(tenantId, d.getId()).orElse(null);
        if (existing != null) return existing;

        OfficialFiscalSubmission s = new OfficialFiscalSubmission();
        s.setTenant(d.getTenant());
        s.setFiscalDocument(d);
        s.setOriginalFiscalDocument(d.getOriginalFiscalDocument());
        s.setDocumentType(d.getDocumentType());
        s.setAuthority(profile.getAuthority());
        s.setEnvironment(profile.getEnvironment());
        s.setIdempotencyKey(idem);
        s.setStatus(OfficialFiscalSubmissionStatus.DRAFT);
        s.setMaxAttempts(props.getMaxAttempts());
        s.setNextAttemptAt(LocalDateTime.now().plusSeconds(props.getInitialDelaySeconds()));
        s = submissionRepository.save(s);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.OFFICIAL_FISCAL_SUBMISSION_CREATED,
                OperationalEntityType.OFFICIAL_FISCAL_SUBMISSION,
                s.getId(),
                OperationalOrigem.SYSTEM,
                "Submissão oficial criada (placeholder)",
                Map.of(
                        "tenantId", tenantId,
                        "submissionId", s.getId(),
                        "fiscalDocumentId", d.getId(),
                        "environment", s.getEnvironment().name()
                ),
                null,
                null
        );
        return s;
    }

    @Transactional
    public void createSubmissionIfAutoMode(Long tenantId, Long fiscalDocumentId, OfficialFiscalSubmissionMode expectedMode) {
        if (!props.isEnabled()) return;
        TenantOfficialFiscalProfile profile = profileRepository.findByTenantId(tenantId).orElse(null);
        if (profile == null || !profile.isOfficialEnabled()) return;
        if (profile.getSubmissionMode() != expectedMode) return;
        // best effort: não lançar exceção para não afetar emissão interna
        try {
            createForDocument(tenantId, fiscalDocumentId);
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public OfficialFiscalDocumentPayload payloadPreview(Long tenantId, Long fiscalDocumentId) {
        TenantOfficialFiscalProfile profile = requireProfile(tenantId);
        FiscalDocument d = fiscalDocumentRepository.findById(fiscalDocumentId).orElseThrow(() -> new BusinessException("FiscalDocument não encontrado."));
        if (d.getTenant() == null || !d.getTenant().getId().equals(tenantId)) throw new BusinessException("Tenant mismatch.");
        List<FiscalDocumentLine> lines = fiscalDocumentLineRepository.findByFiscalDocumentIdOrderByIdAsc(fiscalDocumentId);
        return mapper.mapFromFiscalDocument(d, lines, profile);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOneClaiming(Long tenantId, Long submissionId) {
        if (!props.isEnabled() || !props.isWorkerEnabled()) return;
        TenantOfficialFiscalProfile profile = requireProfile(tenantId);
        if (!profile.isOfficialEnabled()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusMinutes(props.getStaleLockMinutes());
        int claimed = submissionRepository.claimSubmission(
                submissionId,
                now,
                props.getWorkerId(),
                staleCutoff,
                List.of(OfficialFiscalSubmissionStatus.DRAFT, OfficialFiscalSubmissionStatus.FAILED_RETRYABLE, OfficialFiscalSubmissionStatus.PENDING_SUBMISSION)
        );
        if (claimed == 0) return;

        OfficialFiscalSubmission s = submissionRepository.findById(submissionId).orElse(null);
        if (s == null) return;
        if (!s.getTenant().getId().equals(tenantId)) return;

        withTenantContext(tenantId, () -> doProcessOne(profile, s, now));
    }

    private void doProcessOne(TenantOfficialFiscalProfile profile, OfficialFiscalSubmission s, LocalDateTime now) {
        validateProfileSafety(profile);
        if (s.getAttemptCount() >= s.getMaxAttempts()) {
            s.setStatus(OfficialFiscalSubmissionStatus.FAILED_PERMANENT);
            s.setLockedAt(null);
            s.setLockedBy(null);
            s.setOfficialStatusCode("MAX_ATTEMPTS");
            s.setOfficialStatusMessage("Max attempts reached");
            submissionRepository.save(s);
            return;
        }

        FiscalDocument d = requireIssuedFiscalDocument(profile.getTenant().getId(), s.getFiscalDocument().getId());
        List<FiscalDocumentLine> lines = fiscalDocumentLineRepository.findByFiscalDocumentIdOrderByIdAsc(d.getId());
        OfficialFiscalDocumentPayload payload = mapper.mapFromFiscalDocument(d, lines, profile);
        String canonical = canonicalService.canonicalString(payload);
        String payloadHash = canonicalService.sha256Hex(canonical);
        var signed = signingService.signCanonicalPayload(canonical);

        s.setPayloadHash(payloadHash);
        s.setSignedPayloadHash(signed.signedPayloadHash());
        s.setJwsDocumentSignatureHash(signed.jwsPlaceholderHash());
        s.setStatus(OfficialFiscalSubmissionStatus.SIGNED);
        submissionRepository.save(s);

        operationalEventLogService.logGenericForTenant(
                profile.getTenant().getId(),
                OperationalEventType.OFFICIAL_FISCAL_PAYLOAD_SIGNED,
                OperationalEntityType.OFFICIAL_FISCAL_SUBMISSION,
                s.getId(),
                OperationalOrigem.SYSTEM,
                "Payload oficial assinado (placeholder)",
                Map.of(
                        "submissionId", s.getId(),
                        "payloadHash", payloadHash,
                        "signedPayloadHash", s.getSignedPayloadHash()
                ),
                null,
                null
        );

        // Placeholder client: em vez de chamar AGT real, tenta enviar via client abstrato
        String requestId = s.getRequestId() != null ? s.getRequestId() : ("REQ-" + UUID.randomUUID());
        s.setRequestId(requestId);
        s.setStatus(OfficialFiscalSubmissionStatus.SUBMITTED);
        s.setSubmittedAt(now);
        s.setAttemptCount(s.getAttemptCount() + 1);
        s.setLastAttemptAt(now);
        submissionRepository.save(s);

        OfficialFiscalSubmissionAttempt attempt = new OfficialFiscalSubmissionAttempt();
        attempt.setTenant(profile.getTenant());
        attempt.setSubmission(s);
        attempt.setAttemptNumber(s.getAttemptCount());
        attempt.setStatus("SUBMITTED");
        attempt.setRequestId(requestId);
        attempt.setRequestPayloadHash(payloadHash);
        attemptRepository.save(attempt);

        var res = client.submitDocument(requestId, s.getIdempotencyKey(), payload, s.getJwsDocumentSignatureHash());
        if (res != null && res.accepted()) {
            s.setStatus(OfficialFiscalSubmissionStatus.ACCEPTED);
            s.setAcceptedAt(LocalDateTime.now());
            s.setOfficialDocumentId(res.officialDocumentId());
            s.setOfficialStatusCode(res.statusCode());
            s.setOfficialStatusMessage(res.statusMessage());
            s.setLockedAt(null);
            s.setLockedBy(null);
            submissionRepository.save(s);
            attempt.setStatus("ACCEPTED");
            attempt.setHttpStatus(200);
            attempt.setFinishedAt(LocalDateTime.now());
            attemptRepository.save(attempt);
            return;
        }

        // se client estiver desativado, manter PENDING_RESULT para permitir simulação/admin
        s.setStatus(OfficialFiscalSubmissionStatus.PENDING_RESULT);
        s.setOfficialStatusCode(res != null ? res.statusCode() : "UNKNOWN");
        s.setOfficialStatusMessage(res != null ? res.statusMessage() : "No response");
        s.setNextAttemptAt(now.plusSeconds(props.getRetryBackoffSeconds()));
        s.setLockedAt(null);
        s.setLockedBy(null);
        submissionRepository.save(s);

        attempt.setStatus("PENDING_RESULT");
        attempt.setHttpStatus(res != null ? 202 : null);
        attempt.setFinishedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
    }

    @Transactional
    public OfficialFiscalSubmission simulateSubmit(Long tenantId, Long submissionId) {
        ensureSimulationAllowed(tenantId);
        OfficialFiscalSubmission s = getSubmission(tenantId, submissionId);
        if (s == null) throw new BusinessException("Submission não encontrada.");
        if (s.getStatus() == OfficialFiscalSubmissionStatus.ACCEPTED || s.getStatus() == OfficialFiscalSubmissionStatus.REJECTED) {
            return s;
        }
        s.setRequestId(s.getRequestId() != null ? s.getRequestId() : ("SIM-" + UUID.randomUUID()));
        s.setStatus(OfficialFiscalSubmissionStatus.SUBMITTED);
        s.setSubmittedAt(LocalDateTime.now());
        return submissionRepository.save(s);
    }

    @Transactional
    public OfficialFiscalSubmission simulateAccept(Long tenantId, Long submissionId, String code, String message) {
        ensureSimulationAllowed(tenantId);
        OfficialFiscalSubmission s = getSubmission(tenantId, submissionId);
        if (s == null) throw new BusinessException("Submission não encontrada.");
        s.setStatus(OfficialFiscalSubmissionStatus.ACCEPTED);
        s.setAcceptedAt(LocalDateTime.now());
        s.setOfficialStatusCode(code != null ? code : "ACCEPTED");
        s.setOfficialStatusMessage(message != null ? message : "Simulated accept");
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.OFFICIAL_FISCAL_SIMULATION_ACCEPTED,
                OperationalEntityType.OFFICIAL_FISCAL_SUBMISSION,
                s.getId(),
                OperationalOrigem.SYSTEM,
                "Simulação: submissão aceite",
                Map.of("submissionId", s.getId()),
                null,
                null
        );
        return submissionRepository.save(s);
    }

    @Transactional
    public OfficialFiscalSubmission simulateReject(Long tenantId, Long submissionId, String code, String message) {
        ensureSimulationAllowed(tenantId);
        OfficialFiscalSubmission s = getSubmission(tenantId, submissionId);
        if (s == null) throw new BusinessException("Submission não encontrada.");
        s.setStatus(OfficialFiscalSubmissionStatus.REJECTED);
        s.setRejectedAt(LocalDateTime.now());
        s.setOfficialStatusCode(code != null ? code : "REJECTED");
        s.setOfficialStatusMessage(message != null ? message : "Simulated reject");
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.OFFICIAL_FISCAL_SIMULATION_REJECTED,
                OperationalEntityType.OFFICIAL_FISCAL_SUBMISSION,
                s.getId(),
                OperationalOrigem.SYSTEM,
                "Simulação: submissão rejeitada",
                Map.of("submissionId", s.getId()),
                null,
                null
        );
        return submissionRepository.save(s);
    }

    @Transactional
    public OfficialFiscalSubmission cancel(Long tenantId, Long submissionId) {
        OfficialFiscalSubmission s = getSubmission(tenantId, submissionId);
        if (s == null) throw new BusinessException("Submission não encontrada.");
        if (s.getStatus() == OfficialFiscalSubmissionStatus.ACCEPTED) throw new BusinessException("Não pode cancelar submissão ACCEPTED.");
        s.setStatus(OfficialFiscalSubmissionStatus.CANCELLED);
        s.setLockedAt(null);
        s.setLockedBy(null);
        return submissionRepository.save(s);
    }

    @Transactional
    public OfficialFiscalSubmission retry(Long tenantId, Long submissionId) {
        OfficialFiscalSubmission s = getSubmission(tenantId, submissionId);
        if (s == null) throw new BusinessException("Submission não encontrada.");
        if (s.getStatus() == OfficialFiscalSubmissionStatus.ACCEPTED) throw new BusinessException("Não pode retry em ACCEPTED.");
        if (s.getStatus() == OfficialFiscalSubmissionStatus.CANCELLED) throw new BusinessException("Não pode retry em CANCELLED.");
        s.setStatus(OfficialFiscalSubmissionStatus.PENDING_SUBMISSION);
        s.setNextAttemptAt(LocalDateTime.now());
        s.setLockedAt(null);
        s.setLockedBy(null);
        return submissionRepository.save(s);
    }

    private void validateProfileSafety(TenantOfficialFiscalProfile profile) {
        if (profile == null) throw new BusinessException("Official fiscal profile não configurado.");
        if (profile.getEnvironment() == OfficialFiscalEnvironment.PRODUCTION && !props.isAllowProduction()) {
            throw new BusinessException("PRODUCTION bloqueado (consuma.fiscal.official.allow-production=false).");
        }
        if (profile.isOfficialEnabled() && profile.getSigningProfile() == null) {
            throw new BusinessException("Signing profile obrigatório para officialEnabled=true.");
        }
        if (profile.isOfficialEnabled() && profile.getSigningProfile() != null
                && profile.getSigningProfile().getKeyProvider() != null
                && profile.getEnvironment() == OfficialFiscalEnvironment.PRODUCTION
                && (profile.getSigningProfile().getKeyProvider().name().contains("LOCAL") || profile.getSigningProfile().getKeyProvider().name().contains("MANUAL"))) {
            throw new BusinessException("KeyProvider inseguro para PRODUCTION.");
        }
    }

    private void ensureSimulationAllowed(Long tenantId) {
        if (!props.isEnabled()) throw new BusinessException("Official fiscal integration desativada.");
        if (!props.isSimulationEnabled()) throw new BusinessException("Simulação desativada (consuma.fiscal.official.simulation-enabled=false).");
        TenantOfficialFiscalProfile profile = requireProfile(tenantId);
        if (profile.getEnvironment() == OfficialFiscalEnvironment.PRODUCTION) {
            throw new BusinessException("Simulação bloqueada em PRODUCTION.");
        }
    }

    private TenantOfficialFiscalProfile requireProfile(Long tenantId) {
        TenantOfficialFiscalProfile p = profileRepository.findByTenantId(tenantId).orElse(null);
        if (p == null) throw new BusinessException("TenantOfficialFiscalProfile não configurado.");
        return p;
    }

    private FiscalDocument requireIssuedFiscalDocument(Long tenantId, Long docId) {
        FiscalDocument d = fiscalDocumentRepository.findById(docId).orElseThrow(() -> new BusinessException("FiscalDocument não encontrado."));
        if (d.getTenant() == null || !d.getTenant().getId().equals(tenantId)) throw new BusinessException("Tenant mismatch.");
        if (d.getStatus() != FiscalDocumentStatus.ISSUED) throw new BusinessException("FiscalDocument não está ISSUED.");
        return d;
    }

    private static String idempotencyKey(Long tenantId, Long documentId) {
        return "tenant:" + tenantId + ":fiscalDocument:" + documentId + ":official-submission:v1";
    }

    private static void withTenantContext(Long tenantId, Runnable r) {
        TenantContext prev = TenantContextHolder.get().orElse(null);
        try {
            TenantContextHolder.set(new TenantContext(tenantId, null, null, java.util.Set.of(), TenantResolutionSource.LEGACY_NONE, true, false));
            r.run();
        } finally {
            if (prev != null) TenantContextHolder.set(prev); else TenantContextHolder.clear();
        }
    }

    private com.restaurante.model.entity.Tenant requireTenantForId(Long tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("Tenant não encontrado."));
    }
}
