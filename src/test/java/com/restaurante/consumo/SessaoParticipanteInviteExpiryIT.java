package com.restaurante.consumo;

import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.participante.service.SessaoParticipanteExpirationService;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prompt 41.3 — Testes de ciclo de vida: expiração, ACTIVE não afetado, idempotência.
 */
class SessaoParticipanteInviteExpiryIT {

    private SessaoConsumoParticipante buildParticipant(Long id, SessaoParticipanteStatus status, Instant expiresAt, Tenant tenant, SessaoConsumo sessao) {
        SessaoConsumoParticipante p = new SessaoConsumoParticipante();
        p.setId(id);
        p.setTenant(tenant);
        p.setSessaoConsumo(sessao);
        p.setStatus(status);
        p.setExpiresAt(expiresAt);
        return p;
    }

    @Test
    void active_participant_is_not_expired_by_job() {
        SessaoConsumoParticipanteRepository repo = Mockito.mock(SessaoConsumoParticipanteRepository.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setTenantCode("t-1");

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(100L);
        sessao.setTenant(tenant);

        SessaoConsumoParticipante active = buildParticipant(10L, SessaoParticipanteStatus.ACTIVE,
                Instant.now().minusSeconds(3600), tenant, sessao);

        when(repo.findExpiredCandidatesForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(active));

        SessaoParticipanteExpirationService svc = new SessaoParticipanteExpirationService(repo, audit, props);
        var result = svc.expireOnce("batch-active");

        // ACTIVE não deve ter sido expirado
        assertThat(result.expired()).isEqualTo(0);
        assertThat(active.getStatus()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
    }

    @Test
    void invited_participant_with_future_expires_at_is_not_expired() {
        SessaoConsumoParticipanteRepository repo = Mockito.mock(SessaoConsumoParticipanteRepository.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        Tenant tenant = new Tenant();
        tenant.setId(2L);
        tenant.setTenantCode("t-2");

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(200L);
        sessao.setTenant(tenant);

        // expiresAt no futuro — não deve expirar
        SessaoConsumoParticipante invited = buildParticipant(20L, SessaoParticipanteStatus.INVITED,
                Instant.now().plusSeconds(3600), tenant, sessao);

        when(repo.findExpiredCandidatesForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(invited));

        SessaoParticipanteExpirationService svc = new SessaoParticipanteExpirationService(repo, audit, props);
        var result = svc.expireOnce("batch-future");

        assertThat(result.expired()).isEqualTo(0);
        assertThat(invited.getStatus()).isEqualTo(SessaoParticipanteStatus.INVITED);
    }

    @Test
    void job_idempotent_already_expired_skipped() {
        SessaoConsumoParticipanteRepository repo = Mockito.mock(SessaoConsumoParticipanteRepository.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        Tenant tenant = new Tenant();
        tenant.setId(3L);
        tenant.setTenantCode("t-3");

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(300L);
        sessao.setTenant(tenant);

        // já expirado
        SessaoConsumoParticipante already = buildParticipant(30L, SessaoParticipanteStatus.EXPIRED,
                Instant.now().minusSeconds(100), tenant, sessao);

        when(repo.findExpiredCandidatesForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(already));

        SessaoParticipanteExpirationService svc = new SessaoParticipanteExpirationService(repo, audit, props);
        var result = svc.expireOnce("batch-idem");

        assertThat(result.expired()).isEqualTo(0);
    }

    @Test
    void invited_expired_receives_correct_expiration_reason() {
        SessaoConsumoParticipanteRepository repo = Mockito.mock(SessaoConsumoParticipanteRepository.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        Tenant tenant = new Tenant();
        tenant.setId(4L);
        tenant.setTenantCode("t-4");

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(400L);
        sessao.setTenant(tenant);

        SessaoConsumoParticipante invited = buildParticipant(40L, SessaoParticipanteStatus.INVITED,
                Instant.now().minusSeconds(100), tenant, sessao);

        SessaoConsumoParticipante pending = buildParticipant(41L, SessaoParticipanteStatus.PENDING_APPROVAL,
                Instant.now().minusSeconds(100), tenant, sessao);

        when(repo.findExpiredCandidatesForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(invited, pending));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessaoParticipanteExpirationService svc = new SessaoParticipanteExpirationService(repo, audit, props);
        svc.expireOnce("batch-reasons");

        assertThat(invited.getExpirationReason()).isEqualTo("INVITE_TTL_EXPIRED");
        assertThat(pending.getExpirationReason()).isEqualTo("PENDING_APPROVAL_TTL_EXPIRED");
    }

    @Test
    void cancelled_participant_is_not_expired_by_job() {
        SessaoConsumoParticipanteRepository repo = Mockito.mock(SessaoConsumoParticipanteRepository.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        Tenant tenant = new Tenant();
        tenant.setId(5L);
        tenant.setTenantCode("t-5");

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(500L);
        sessao.setTenant(tenant);

        SessaoConsumoParticipante cancelled = buildParticipant(50L, SessaoParticipanteStatus.CANCELLED,
                Instant.now().minusSeconds(100), tenant, sessao);

        when(repo.findExpiredCandidatesForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(cancelled));

        SessaoParticipanteExpirationService svc = new SessaoParticipanteExpirationService(repo, audit, props);
        var result = svc.expireOnce("batch-cancelled");

        assertThat(result.expired()).isEqualTo(0);
        assertThat(cancelled.getStatus()).isEqualTo(SessaoParticipanteStatus.CANCELLED);
    }
}
