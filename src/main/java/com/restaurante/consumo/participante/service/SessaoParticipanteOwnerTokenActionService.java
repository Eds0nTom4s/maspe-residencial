package com.restaurante.consumo.participante.service;

import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Prompt 41.5 — Serviço de orquestração de ações de gestão via ownerActionToken.
 * <p>
 * Responsabilidades:
 * - Resolver contexto QR via {@link SessaoConsumoParticipanteService#resolveQrContext}
 * - Validar token via {@link SessaoOwnerActionTokenService#validateAndUse}
 * - Verificar que o OWNER continua ACTIVE e com role OWNER
 * - Carregar participante alvo com lock
 * - Delegar para lógica de domínio existente (41.2/41.3)
 * - Registrar auditoria sanitizada (sem raw token)
 * <p>
 * SEGURANÇA:
 * - Raw token nunca é logado nem exposto em metadados
 * - Token de outra sessão → SESSION_MISMATCH
 * - Token de outro tenant → INVALID
 * - OWNER que não é mais ACTIVE/OWNER → OWNER_NOT_ACTIVE / OWNER_NOT_OWNER
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoParticipanteOwnerTokenActionService {

    private final SessaoConsumoParticipanteService participanteService;
    private final SessaoOwnerActionTokenService ownerTokenService;
    private final SessaoConsumoParticipanteRepository participanteRepository;
    private final OperationalEventLogService eventLogService;

    // =========================================================================
    // Aprovar participante
    // =========================================================================

    /**
     * Aprova participante em PENDING_APPROVAL usando ownerActionToken (sem OTP direto).
     */
    @Transactional
    public ApproveResult approveByOwnerToken(String qrToken,
                                             Long participanteId,
                                             String rawOwnerActionToken,
                                             String reason,
                                             String ip,
                                             String userAgent) {
        // 1. Resolver contexto e validar token
        var ctx = resolveAndValidate(qrToken, rawOwnerActionToken, ip, userAgent);
        SessaoConsumoParticipante owner = ctx.owner();
        SessaoConsumo sessao = ctx.sessao();
        Tenant tenant = ctx.tenant();

        // 2. Carregar participante alvo com lock
        SessaoConsumoParticipante p = loadWithLock(tenant.getId(), participanteId, sessao.getId());

        // 3. Validar estado
        if (p.getStatus() == SessaoParticipanteStatus.ACTIVE) {
            throw new BusinessException("PARTICIPANT_ALREADY_ACTIVE");
        }
        if (p.getStatus() != SessaoParticipanteStatus.PENDING_APPROVAL) {
            throw new BusinessException("PARTICIPANTE_NOT_PENDING_APPROVAL");
        }

        // 4. Verificar TTL
        Instant now = Instant.now();
        checkNotExpired(p, now);

        // 5. Aprovar
        SessaoParticipanteStatus oldStatus = p.getStatus();
        p.setStatus(SessaoParticipanteStatus.ACTIVE);
        if (p.getJoinedAt() == null) p.setJoinedAt(now);
        p.setLastActivityAt(now);
        p.setExpiresAt(null);
        p.setApprovedByParticipanteId(owner.getId());
        p.setApprovalDecidedAt(now);
        p.setApprovalReason(sanitize(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        // 6. Auditoria (sem raw token)
        eventLogService.logPublicEvent(
                tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_APPROVED_BY_OWNER_TOKEN,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Participante aprovado via ownerActionToken",
                Map.of(
                        "tenantId", tenant.getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "participanteId", saved.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "oldStatus", oldStatus.name(),
                        "newStatus", saved.getStatus().name()
                ),
                ip, userAgent
        );

        log.info("[OwnerToken] Participante {} aprovado pelo OWNER {} (sessao={}, tenant={})",
                saved.getId(), owner.getId(), sessao.getId(), tenant.getId());

        return new ApproveResult(saved.getId(), saved.getStatus(), now, owner.getId());
    }

    // =========================================================================
    // Rejeitar participante
    // =========================================================================

    /**
     * Rejeita participante em PENDING_APPROVAL usando ownerActionToken.
     */
    @Transactional
    public RejectResult rejectByOwnerToken(String qrToken,
                                           Long participanteId,
                                           String rawOwnerActionToken,
                                           String reason,
                                           String ip,
                                           String userAgent) {
        var ctx = resolveAndValidate(qrToken, rawOwnerActionToken, ip, userAgent);
        SessaoConsumoParticipante owner = ctx.owner();
        SessaoConsumo sessao = ctx.sessao();
        Tenant tenant = ctx.tenant();

        SessaoConsumoParticipante p = loadWithLock(tenant.getId(), participanteId, sessao.getId());

        if (p.getStatus() == SessaoParticipanteStatus.ACTIVE) {
            throw new BusinessException("PARTICIPANTE_NOT_REJECTABLE");
        }
        if (p.getStatus() != SessaoParticipanteStatus.PENDING_APPROVAL) {
            throw new BusinessException("PARTICIPANTE_NOT_REJECTABLE");
        }

        Instant now = Instant.now();
        checkNotExpired(p, now);

        SessaoParticipanteStatus oldStatus = p.getStatus();
        p.setStatus(SessaoParticipanteStatus.REJECTED);
        p.setRejectedByParticipanteId(owner.getId());
        p.setApprovalDecidedAt(now);
        p.setRejectionReason(sanitize(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        eventLogService.logPublicEvent(
                tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_REJECTED_BY_OWNER_TOKEN,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Participante rejeitado via ownerActionToken",
                Map.of(
                        "tenantId", tenant.getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "participanteId", saved.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "oldStatus", oldStatus.name(),
                        "newStatus", saved.getStatus().name()
                ),
                ip, userAgent
        );

        return new RejectResult(saved.getId(), saved.getStatus(), now, owner.getId());
    }

    // =========================================================================
    // Cancelar convite/pendência
    // =========================================================================

    /**
     * Cancela participante em INVITED / PENDING_OTP / PENDING_APPROVAL via ownerActionToken.
     * Idempotente: se já CANCELLED, retorna estado atual sem reauditar.
     */
    @Transactional
    public CancelResult cancelByOwnerToken(String qrToken,
                                           Long participanteId,
                                           String rawOwnerActionToken,
                                           String reason,
                                           String ip,
                                           String userAgent) {
        var ctx = resolveAndValidate(qrToken, rawOwnerActionToken, ip, userAgent);
        SessaoConsumoParticipante owner = ctx.owner();
        SessaoConsumo sessao = ctx.sessao();
        Tenant tenant = ctx.tenant();

        SessaoConsumoParticipante p = loadWithLock(tenant.getId(), participanteId, sessao.getId());

        // Idempotente
        if (p.getStatus() == SessaoParticipanteStatus.CANCELLED) {
            return new CancelResult(p.getId(), p.getStatus(), p.getCancelledAt(), owner.getId(), false);
        }

        if (p.getStatus() == SessaoParticipanteStatus.ACTIVE) {
            throw new BusinessException("PARTICIPANTE_NOT_CANCELABLE");
        }
        if (p.getStatus() != SessaoParticipanteStatus.INVITED
                && p.getStatus() != SessaoParticipanteStatus.PENDING_OTP
                && p.getStatus() != SessaoParticipanteStatus.PENDING_APPROVAL) {
            throw new BusinessException("PARTICIPANTE_NOT_CANCELABLE");
        }

        Instant now = Instant.now();
        SessaoParticipanteStatus oldStatus = p.getStatus();
        p.setStatus(SessaoParticipanteStatus.CANCELLED);
        p.setCancelledAt(now);
        p.setCancelledByParticipanteId(owner.getId());
        p.setCancellationReason(sanitize(reason));
        SessaoConsumoParticipante saved = participanteRepository.save(p);

        eventLogService.logPublicEvent(
                tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_CANCELLED_BY_OWNER_TOKEN,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                saved.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Participante cancelado via ownerActionToken",
                Map.of(
                        "tenantId", tenant.getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "participanteId", saved.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "oldStatus", oldStatus.name(),
                        "newStatus", saved.getStatus().name()
                ),
                ip, userAgent
        );

        return new CancelResult(saved.getId(), saved.getStatus(), now, owner.getId(), true);
    }

    // =========================================================================
    // Reenviar convite
    // =========================================================================

    /**
     * Reenvia convite via ownerActionToken.
     * Delega para a lógica existente do 41.3 (cooldown, maxResends, OTP).
     */
    @Transactional
    public ResendResult resendInviteByOwnerToken(String qrToken,
                                                  Long participanteId,
                                                  String rawOwnerActionToken,
                                                  String ip,
                                                  String userAgent) {
        var ctx = resolveAndValidate(qrToken, rawOwnerActionToken, ip, userAgent);
        SessaoConsumoParticipante owner = ctx.owner();
        SessaoConsumo sessao = ctx.sessao();
        Tenant tenant = ctx.tenant();

        // Verificar participante pertence à sessão
        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(tenant.getId(), participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessao.getId())) {
            throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        }

        // Verificar resendable antes de delegar
        if (!participanteService.canResendInviteNow(p)) {
            throw new BusinessException("PARTICIPANTE_INVITE_NOT_RESENDABLE");
        }

        // Delegar para serviço existente 41.3 (via qrToken)
        // Para evitar duplicação de lógica de SMS, delegamos internamente
        var otpResult = participanteService.resendInviteByOwnerTokenInternal(owner, p, ip, userAgent);

        eventLogService.logPublicEvent(
                tenant, sessao.getInstituicao(), sessao.getUnidadeAtendimento(), sessao.getMesa(), null,
                OperationalEventType.SESSAO_PARTICIPANTE_INVITE_RESENT_BY_OWNER_TOKEN,
                OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                p.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Convite reenviado via ownerActionToken",
                Map.of(
                        "tenantId", tenant.getId(),
                        "sessaoConsumoId", sessao.getId(),
                        "participanteId", p.getId(),
                        "ownerParticipanteId", owner.getId(),
                        "resendCount", p.getResendCount(),
                        "smsSent", otpResult.isSmsSent()
                ),
                ip, userAgent
        );

        boolean canResendNow = participanteService.canResendInviteNow(p);
        return new ResendResult(p.getId(), p.getStatus(), true, p.getResendCount(), p.getLastResendAt(), canResendNow);
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    /**
     * Resolve o contexto QR, valida o ownerActionToken e verifica que o OWNER
     * continua ACTIVE com role OWNER.
     */
    private TokenActionContext resolveAndValidate(String qrToken, String rawOwnerActionToken, String ip, String userAgent) {
        // 1. Resolver contexto QR
        SessaoConsumoParticipanteService.QrContext qrCtx = participanteService.resolveQrContext(qrToken);

        // 2. Validar token (lança BusinessException em caso de falha)
        SessaoOwnerActionTokenService.ValidateResult tokenResult =
                ownerTokenService.validateAndUse(qrCtx.tenantId(), qrCtx.sessaoId(), rawOwnerActionToken, ip, userAgent);

        SessaoConsumoParticipante owner = tokenResult.ownerParticipante();

        // 3. Verificar que o OWNER ainda está ACTIVE
        if (owner.getStatus() != SessaoParticipanteStatus.ACTIVE) {
            throw new BusinessException("OWNER_ACTION_TOKEN_OWNER_NOT_ACTIVE");
        }

        // 4. Verificar que o OWNER ainda tem role OWNER
        if (owner.getRole() != SessaoParticipanteRole.OWNER) {
            throw new BusinessException("OWNER_ACTION_TOKEN_OWNER_NOT_OWNER");
        }

        return new TokenActionContext(owner, tokenResult.sessaoConsumo(), tokenResult.tenant());
    }

    private SessaoConsumoParticipante loadWithLock(Long tenantId, Long participanteId, Long sessaoId) {
        SessaoConsumoParticipante p = participanteRepository.findForUpdateById(tenantId, participanteId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSAO_PARTICIPANTE_NOT_FOUND"));
        if (!p.getSessaoConsumo().getId().equals(sessaoId)) {
            throw new BusinessException("SESSAO_PARTICIPANTE_WRONG_SESSION");
        }
        return p;
    }

    private void checkNotExpired(SessaoConsumoParticipante p, Instant now) {
        if (p.getExpiresAt() != null && p.getExpiresAt().isBefore(now)) {
            p.setStatus(SessaoParticipanteStatus.EXPIRED);
            p.setExpiredAt(now);
            p.setExpirationReason("PENDING_APPROVAL_TTL_EXPIRED");
            participanteRepository.save(p);
            throw new BusinessException("PARTICIPANT_REQUEST_EXPIRED");
        }
    }

    private String sanitize(String s) {
        if (s == null) return null;
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    // =========================================================================
    // Records de resultado
    // =========================================================================

    private record TokenActionContext(
            SessaoConsumoParticipante owner,
            SessaoConsumo sessao,
            Tenant tenant
    ) {}

    public record ApproveResult(
            Long participanteId,
            SessaoParticipanteStatus status,
            Instant approvedAt,
            Long ownerParticipanteId
    ) {}

    public record RejectResult(
            Long participanteId,
            SessaoParticipanteStatus status,
            Instant rejectedAt,
            Long ownerParticipanteId
    ) {}

    public record CancelResult(
            Long participanteId,
            SessaoParticipanteStatus status,
            Instant cancelledAt,
            Long ownerParticipanteId,
            boolean wasCancelled
    ) {}

    public record ResendResult(
            Long participanteId,
            SessaoParticipanteStatus status,
            boolean resent,
            int resendCount,
            Instant lastResendAt,
            boolean canResend
    ) {}
}
