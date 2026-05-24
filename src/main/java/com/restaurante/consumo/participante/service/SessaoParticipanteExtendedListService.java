package com.restaurante.consumo.participante.service;

import com.restaurante.config.SessaoOwnerActionTokenProperties;
import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import com.restaurante.config.SessaoParticipanteListProperties;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.entity.SessaoParticipanteLifecycleJobRun;
import com.restaurante.consumo.participante.job.SessaoParticipanteExpirationJob;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.participante.repository.SessaoParticipanteLifecycleJobRunRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Prompt 41.4 — Serviço de listagens ampliadas, pending-all, histórico de convites e health check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoParticipanteExtendedListService {

    private final SessaoConsumoParticipanteRepository participanteRepository;
    private final SessaoParticipanteLifecycleJobRunRepository jobRunRepository;
    private final SessaoOwnerActionTokenService ownerActionTokenService;
    private final SessaoConsumoParticipanteService participanteService;
    private final TelefoneNormalizerService telefoneNormalizerService;
    private final SessaoParticipanteListProperties listProps;
    private final SessaoParticipanteLifecycleProperties lifecycleProps;
    private final SessaoOwnerActionTokenProperties tokenProps;
    private final OperationalEventLogService operationalEventLogService;

    // -------------------------------------------------------------------------
    // Listagem unificada — público (sem/com ownerActionToken)
    // -------------------------------------------------------------------------

    /**
     * Listagem unificada pública.
     * Sem token: apenas ACTIVE, dados mínimos.
     * Com token válido: visão ampliada, mas sempre sanitizada.
     */
    @Transactional(readOnly = true)
    public ParticipantePage listAllPublic(String qrToken,
                                          String rawOwnerActionToken,
                                          Long sessaoId,
                                          Long tenantId,
                                          SessaoParticipanteStatus statusFilter,
                                          SessaoParticipanteRole roleFilter,
                                          boolean includeInactive,
                                          int page, int size,
                                          String ip, String userAgent) {
        validatePageSize(size);
        PageRequest pageable = PageRequest.of(page, effectiveSize(size), Sort.by("joinedAt").ascending());

        boolean hasToken = rawOwnerActionToken != null && !rawOwnerActionToken.isBlank();
        SessaoOwnerActionTokenService.ValidateResult tokenResult = null;

        if (hasToken) {
            tokenResult = ownerActionTokenService.validateAndUse(tenantId, sessaoId, rawOwnerActionToken, ip, userAgent);
        }

        // Sem token: apenas ACTIVE
        SessaoParticipanteStatus effectiveStatus = hasToken ? statusFilter : SessaoParticipanteStatus.ACTIVE;

        Page<SessaoConsumoParticipante> dbPage = participanteRepository.listBySessaoPaged(
                tenantId, sessaoId, effectiveStatus, roleFilter, pageable);

        final boolean withToken = hasToken;
        final SessaoOwnerActionTokenService.ValidateResult finalTokenResult = tokenResult;
        List<ParticipanteItemView> items = dbPage.getContent().stream().map(p ->
                toItemView(p, withToken, participanteService.canResendInviteNow(p))
        ).toList();

        if (withToken) {
            Tenant tenant = finalTokenResult.tenant();
            SessaoConsumo sessao = finalTokenResult.sessaoConsumo();
            operationalEventLogService.logPublicEvent(
                    tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                    OperationalEventType.SESSAO_PARTICIPANTE_LIST_EXTENDED_VIEWED,
                    OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                    finalTokenResult.ownerParticipante().getId(),
                    OperationalOrigem.QR_PUBLICO,
                    "Listagem ampliada de participantes (OWNER)",
                    Map.of("tenantId", tenantId, "sessaoConsumoId", sessaoId, "page", page, "size", size),
                    ip, userAgent
            );
        }

        return new ParticipantePage(items, dbPage.getNumber(), dbPage.getSize(),
                dbPage.getTotalElements(), dbPage.getTotalPages(), withToken);
    }

    // -------------------------------------------------------------------------
    // Pending-All (OWNER via token)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ParticipantePage listPendingAll(Long tenantId, Long sessaoId,
                                           SessaoOwnerActionTokenService.ValidateResult tokenResult,
                                           List<String> statusNames,
                                           Boolean canResendFilter,
                                           int page, int size,
                                           String ip, String userAgent) {
        validatePageSize(size);
        PageRequest pageable = PageRequest.of(page, effectiveSize(size), Sort.by("createdAt").descending());

        Collection<SessaoParticipanteStatus> statuses = resolveStatuses(statusNames);
        Page<SessaoConsumoParticipante> dbPage = participanteRepository
                .listBySessaoAndStatuses(tenantId, sessaoId, statuses, pageable);

        Instant now = Instant.now();
        List<ParticipanteItemView> items = dbPage.getContent().stream()
                .filter(p -> {
                    if (Boolean.TRUE.equals(canResendFilter)) {
                        return participanteService.canResendInviteNow(p);
                    }
                    return true;
                })
                .map(p -> toItemView(p, true, participanteService.canResendInviteNow(p)))
                .toList();

        Tenant tenant = tokenResult.tenant();
        SessaoConsumo sessao = tokenResult.sessaoConsumo();
        operationalEventLogService.logPublicEvent(
                tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_PENDING_ALL_VIEWED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                tokenResult.ownerParticipante().getId(),
                OperationalOrigem.QR_PUBLICO,
                "Listagem pending-all (OWNER)",
                Map.of("tenantId", tenantId, "sessaoConsumoId", sessaoId,
                        "statusFilter", statusNames != null ? statusNames : "default",
                        "page", page, "size", size),
                ip, userAgent
        );

        return new ParticipantePage(items, dbPage.getNumber(), dbPage.getSize(),
                dbPage.getTotalElements(), dbPage.getTotalPages(), true);
    }

    // -------------------------------------------------------------------------
    // Pending-All — Device/POS
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ParticipantePage listPendingAllByDevice(Long tenantId, Long sessaoId,
                                                   List<String> statusNames,
                                                   Boolean canResendFilter,
                                                   int page, int size) {
        validatePageSize(size);
        PageRequest pageable = PageRequest.of(page, effectiveSize(size), Sort.by("createdAt").descending());

        Collection<SessaoParticipanteStatus> statuses = resolveStatuses(statusNames);
        Page<SessaoConsumoParticipante> dbPage = participanteRepository
                .listBySessaoAndStatuses(tenantId, sessaoId, statuses, pageable);

        List<ParticipanteItemView> items = dbPage.getContent().stream()
                .filter(p -> {
                    if (Boolean.TRUE.equals(canResendFilter)) return participanteService.canResendInviteNow(p);
                    return true;
                })
                .map(p -> toItemView(p, true, participanteService.canResendInviteNow(p)))
                .toList();

        return new ParticipantePage(items, dbPage.getNumber(), dbPage.getSize(),
                dbPage.getTotalElements(), dbPage.getTotalPages(), true);
    }

    // -------------------------------------------------------------------------
    // Listagem device ampliada
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ParticipantePage listAllByDevice(Long tenantId, Long sessaoId,
                                            SessaoParticipanteStatus statusFilter,
                                            SessaoParticipanteRole roleFilter,
                                            int page, int size) {
        validatePageSize(size);
        PageRequest pageable = PageRequest.of(page, effectiveSize(size), Sort.by("joinedAt").ascending());
        Page<SessaoConsumoParticipante> dbPage = participanteRepository
                .listBySessaoPaged(tenantId, sessaoId, statusFilter, roleFilter, pageable);

        List<ParticipanteItemView> items = dbPage.getContent().stream()
                .map(p -> toItemView(p, true, participanteService.canResendInviteNow(p)))
                .toList();

        return new ParticipantePage(items, dbPage.getNumber(), dbPage.getSize(),
                dbPage.getTotalElements(), dbPage.getTotalPages(), true);
    }

    // -------------------------------------------------------------------------
    // Histórico de convites do OWNER
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ParticipantePage listOwnerInviteHistory(Long tenantId, Long sessaoId,
                                                   SessaoOwnerActionTokenService.ValidateResult tokenResult,
                                                   SessaoParticipanteStatus statusFilter,
                                                   int page, int size,
                                                   String ip, String userAgent) {
        validatePageSize(size);
        PageRequest pageable = PageRequest.of(page, effectiveSize(size), Sort.by("invitedAt").descending());

        Long ownerParticipanteId = tokenResult.ownerParticipante().getId();
        Page<SessaoConsumoParticipante> dbPage = participanteRepository
                .listOwnerSentInvites(tenantId, sessaoId, ownerParticipanteId, pageable);

        List<ParticipanteItemView> items = dbPage.getContent().stream()
                .filter(p -> statusFilter == null || p.getStatus() == statusFilter)
                .map(p -> toItemView(p, true, participanteService.canResendInviteNow(p)))
                .toList();

        Tenant tenant = tokenResult.tenant();
        SessaoConsumo sessao = tokenResult.sessaoConsumo();
        operationalEventLogService.logPublicEvent(
                tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_OWNER_INVITES_VIEWED,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                ownerParticipanteId,
                OperationalOrigem.QR_PUBLICO,
                "Histórico de convites do OWNER",
                Map.of("tenantId", tenantId, "sessaoConsumoId", sessaoId,
                        "ownerParticipanteId", ownerParticipanteId,
                        "page", page, "size", size),
                ip, userAgent
        );

        return new ParticipantePage(items, dbPage.getNumber(), dbPage.getSize(),
                dbPage.getTotalElements(), dbPage.getTotalPages(), true);
    }

    // -------------------------------------------------------------------------
    // Health check do job de expiração
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public JobHealthView getJobHealth(Tenant tenant, String ip, String userAgent) {
        List<SessaoParticipanteLifecycleJobRun> lastRuns = jobRunRepository
                .findLastRunsByJobName(SessaoParticipanteExpirationJob.JOB_NAME, PageRequest.of(0, 1));

        SessaoParticipanteLifecycleJobRun last = lastRuns.isEmpty() ? null : lastRuns.get(0);

        if (tenant != null) {
            operationalEventLogService.logPublicEvent(
                    tenant, null, null, null, null,
                    OperationalEventType.SESSAO_PARTICIPANTE_LIFECYCLE_JOB_HEALTH_VIEWED,
                    OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                    null,
                    OperationalOrigem.SYSTEM,
                    "Job health consultado",
                    Map.of("tenantId", tenant.getId()),
                    ip, userAgent
            );
        }

        JobHealthConfig config = new JobHealthConfig(
                lifecycleProps.isExpirationJobEnabled(),
                lifecycleProps.getInviteTtlMinutes(),
                lifecycleProps.getPendingApprovalTtlMinutes(),
                lifecycleProps.getExpirationBatchSize()
        );

        if (last == null) {
            return new JobHealthView(
                    lifecycleProps.isExpirationJobEnabled(),
                    null, null, null, null, null,
                    config
            );
        }

        return new JobHealthView(
                lifecycleProps.isExpirationJobEnabled(),
                last.getStartedAt(),
                last.getStatus(),
                last.getExpiredCount(),
                last.getScannedCount(),
                last.getErrorMessage(),
                config
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ParticipanteItemView toItemView(SessaoConsumoParticipante p, boolean withToken, boolean canResend) {
        Instant now = Instant.now();
        Long ageSeconds = p.getCreatedAt() != null
                ? now.getEpochSecond() - p.getCreatedAt().getEpochSecond() : null;
        Long expiresInSeconds = (p.getExpiresAt() != null && p.getExpiresAt().isAfter(now))
                ? p.getExpiresAt().getEpochSecond() - now.getEpochSecond() : null;

        String source = resolveSource(p);
        String telefoneMascarado = withToken
                ? telefoneNormalizerService.mask(p.getTelefoneNormalizado())
                : null;

        return new ParticipanteItemView(
                p.getId(),
                p.getNomeExibicao(),
                p.getRole(),
                p.getStatus(),
                p.getJoinedAt(),
                p.getInvitedAt(),
                p.getExpiresAt(),
                p.getExpiredAt(),
                p.getCancelledAt(),
                p.getApprovalRequestedAt(),
                p.getResendCount(),
                p.getLastResendAt(),
                canResend,
                ageSeconds,
                expiresInSeconds,
                source,
                telefoneMascarado
        );
    }

    private String resolveSource(SessaoConsumoParticipante p) {
        if (p.getInvitedByParticipanteId() != null) return "OWNER_INVITE";
        if (p.getInvitedByDeviceId() != null) return "POS_INVITE";
        if (p.getAddedByDevice() != null) return "POS_ADD";
        return "PUBLIC_QR";
    }

    private Collection<SessaoParticipanteStatus> resolveStatuses(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of(
                    SessaoParticipanteStatus.INVITED,
                    SessaoParticipanteStatus.PENDING_OTP,
                    SessaoParticipanteStatus.PENDING_APPROVAL
            );
        }
        List<SessaoParticipanteStatus> result = new ArrayList<>();
        for (String name : names) {
            try {
                result.add(SessaoParticipanteStatus.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_PARTICIPANT_STATUS_FILTER");
            }
        }
        return result;
    }

    private void validatePageSize(int size) {
        if (size > listProps.getMaxPageSize()) {
            throw new BusinessException("PAGE_SIZE_TOO_LARGE");
        }
    }

    private int effectiveSize(int size) {
        return size <= 0 ? listProps.getDefaultPageSize()
                : Math.min(size, listProps.getMaxPageSize());
    }

    // -------------------------------------------------------------------------
    // DTOs internos
    // -------------------------------------------------------------------------

    public record ParticipanteItemView(
            Long participanteId,
            String nomeExibicao,
            SessaoParticipanteRole role,
            SessaoParticipanteStatus status,
            Instant joinedAt,
            Instant invitedAt,
            Instant expiresAt,
            Instant expiredAt,
            Instant cancelledAt,
            Instant approvalRequestedAt,
            int resendCount,
            Instant lastResendAt,
            boolean canResend,
            Long ageSeconds,
            Long expiresInSeconds,
            String source,
            String telefoneMascarado
    ) {}

    public record ParticipantePage(
            List<ParticipanteItemView> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean extended
    ) {}

    public record JobHealthView(
            boolean jobEnabled,
            Instant lastRunAt,
            String lastStatus,
            Integer lastExpiredCount,
            Integer lastScannedCount,
            String lastError,
            JobHealthConfig config
    ) {}

    public record JobHealthConfig(
            boolean enabled,
            int inviteTtlMinutes,
            int pendingApprovalTtlMinutes,
            int batchSize
    ) {}
}
