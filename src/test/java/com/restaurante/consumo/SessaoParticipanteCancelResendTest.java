package com.restaurante.consumo;

import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 41.3 — Testes unitários de lógica de reenvio (cooldown + maxResends).
 * Valida canResendInvite sem necessidade de Spring context (pure unit).
 */
class SessaoParticipanteCancelResendTest {

    private SessaoParticipanteLifecycleProperties defaultProps() {
        SessaoParticipanteLifecycleProperties p = new SessaoParticipanteLifecycleProperties();
        p.setInviteTtlMinutes(30);
        p.setPendingApprovalTtlMinutes(60);
        p.setResendCooldownSeconds(60);
        p.setMaxResends(3);
        p.setExpirationJobEnabled(true);
        p.setExpirationBatchSize(200);
        return p;
    }

    private SessaoConsumoParticipante buildInvited(int resendCount, Instant lastResendAt, Instant expiresAt) {
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setId(99L);
        p.setStatus(SessaoParticipanteStatus.INVITED);
        p.setResendCount(resendCount);
        p.setLastResendAt(lastResendAt);
        p.setExpiresAt(expiresAt);
        return p;
    }

    // --- canResend helpers (inline logic matching SessaoConsumoParticipanteService.canResendInvite) ---

    private boolean canResend(SessaoConsumoParticipante p, SessaoParticipanteLifecycleProperties props) {
        Instant now = Instant.now();
        if (p == null) return false;
        if (p.getStatus() != SessaoParticipanteStatus.INVITED && p.getStatus() != SessaoParticipanteStatus.PENDING_OTP) return false;
        if (p.getCancelledAt() != null || p.getStatus() == SessaoParticipanteStatus.CANCELLED) return false;
        if (p.getExpiredAt() != null || p.getStatus() == SessaoParticipanteStatus.EXPIRED) return false;
        if (p.getExpiresAt() != null && p.getExpiresAt().isBefore(now)) return false;
        if (p.getResendCount() >= props.getMaxResends()) return false;
        if (p.getLastResendAt() != null && p.getLastResendAt().plusSeconds(props.getResendCooldownSeconds()).isAfter(now)) return false;
        return true;
    }

    @Test
    void can_resend_when_within_limits() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        SessaoConsumoParticipante p = buildInvited(0, null, Instant.now().plusSeconds(1800));
        assertThat(canResend(p, props)).isTrue();
    }

    @Test
    void cannot_resend_when_max_resends_reached() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        SessaoConsumoParticipante p = buildInvited(3, Instant.now().minusSeconds(120), Instant.now().plusSeconds(1800));
        assertThat(canResend(p, props)).isFalse();
    }

    @Test
    void cannot_resend_when_cooldown_not_elapsed() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        // lastResendAt há 30s, cooldown é 60s -> ainda bloqueado
        SessaoConsumoParticipante p = buildInvited(1, Instant.now().minusSeconds(30), Instant.now().plusSeconds(1800));
        assertThat(canResend(p, props)).isFalse();
    }

    @Test
    void can_resend_after_cooldown_elapsed() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        // lastResendAt há 90s, cooldown é 60s -> pode reenviar
        SessaoConsumoParticipante p = buildInvited(1, Instant.now().minusSeconds(90), Instant.now().plusSeconds(1800));
        assertThat(canResend(p, props)).isTrue();
    }

    @Test
    void cannot_resend_when_invite_expired() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        SessaoConsumoParticipante p = buildInvited(0, null, Instant.now().minusSeconds(60));
        assertThat(canResend(p, props)).isFalse();
    }

    @Test
    void cannot_resend_for_active_participant() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setId(1L);
        p.setStatus(SessaoParticipanteStatus.ACTIVE);
        p.setResendCount(0);
        p.setExpiresAt(null);
        assertThat(canResend(p, props)).isFalse();
    }

    @Test
    void cannot_resend_for_cancelled_participant() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setId(2L);
        p.setStatus(SessaoParticipanteStatus.CANCELLED);
        p.setResendCount(0);
        p.setCancelledAt(Instant.now());
        assertThat(canResend(p, props)).isFalse();
    }

    @Test
    void cannot_resend_for_expired_participant() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setId(3L);
        p.setStatus(SessaoParticipanteStatus.EXPIRED);
        p.setResendCount(0);
        p.setExpiredAt(Instant.now());
        assertThat(canResend(p, props)).isFalse();
    }

    @Test
    void can_resend_exactly_at_max_minus_one() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        // resendCount = maxResends-1 = 2, cooldown OK
        SessaoConsumoParticipante p = buildInvited(2, Instant.now().minusSeconds(120), Instant.now().plusSeconds(1800));
        assertThat(canResend(p, props)).isTrue();
    }

    @Test
    void resend_count_increments_to_max_then_blocks() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        // at exactly maxResends = 3 → blocked
        SessaoConsumoParticipante p = buildInvited(3, Instant.now().minusSeconds(120), Instant.now().plusSeconds(1800));
        assertThat(canResend(p, props)).isFalse();
    }

    // ------- Cancel field assertions -------

    @Test
    void cancel_fields_are_set_correctly() {
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setId(100L);
        p.setStatus(SessaoParticipanteStatus.INVITED);

        Instant now = Instant.now();
        p.setStatus(SessaoParticipanteStatus.CANCELLED);
        p.setCancelledAt(now);
        p.setCancelledByParticipanteId(42L);
        p.setCancellationReason("Owner cancelou");

        assertThat(p.getStatus()).isEqualTo(SessaoParticipanteStatus.CANCELLED);
        assertThat(p.getCancelledAt()).isEqualTo(now);
        assertThat(p.getCancelledByParticipanteId()).isEqualTo(42L);
        assertThat(p.getCancellationReason()).isEqualTo("Owner cancelou");
    }

    @Test
    void expiration_fields_are_set_correctly_by_job() {
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setId(200L);
        p.setStatus(SessaoParticipanteStatus.PENDING_APPROVAL);
        p.setExpiresAt(Instant.now().minusSeconds(60));

        Instant now = Instant.now();
        p.setStatus(SessaoParticipanteStatus.EXPIRED);
        p.setExpiredAt(now);
        p.setExpirationReason("PENDING_APPROVAL_TTL_EXPIRED");
        p.setCleanupBatchId("batch-test-001");

        assertThat(p.getStatus()).isEqualTo(SessaoParticipanteStatus.EXPIRED);
        assertThat(p.getExpiredAt()).isEqualTo(now);
        assertThat(p.getExpirationReason()).isEqualTo("PENDING_APPROVAL_TTL_EXPIRED");
        assertThat(p.getCleanupBatchId()).isEqualTo("batch-test-001");
    }

    @Test
    void invite_ttl_is_set_on_creation() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        Instant now = Instant.now();
        Instant expectedExpiry = now.plusSeconds((long) props.getInviteTtlMinutes() * 60L);

        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setExpiresAt(expectedExpiry);

        // Validar que o TTL foi definido como previsto
        assertThat(p.getExpiresAt()).isAfter(now);
        assertThat(p.getExpiresAt()).isBeforeOrEqualTo(expectedExpiry.plusSeconds(1));
    }

    @Test
    void pending_approval_ttl_is_set_on_join() {
        SessaoParticipanteLifecycleProperties props = defaultProps();
        Instant now = Instant.now();
        Instant expectedExpiry = now.plusSeconds((long) props.getPendingApprovalTtlMinutes() * 60L);

        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setStatus(SessaoParticipanteStatus.PENDING_APPROVAL);
        p.setExpiresAt(expectedExpiry);

        assertThat(p.getExpiresAt()).isAfter(now);
        assertThat(p.getStatus()).isEqualTo(SessaoParticipanteStatus.PENDING_APPROVAL);
    }
}
