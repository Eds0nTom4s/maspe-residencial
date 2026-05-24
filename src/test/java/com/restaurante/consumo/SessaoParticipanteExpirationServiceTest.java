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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SessaoParticipanteExpirationServiceTest {

    @Test
    void expires_invited_and_pending_approval_when_expires_at_passed() {
        SessaoConsumoParticipanteRepository repo = Mockito.mock(SessaoConsumoParticipanteRepository.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);
        props.setExpirationBatchSize(200);

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setTenantCode("t-10");

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(200L);
        sessao.setTenant(tenant);

        SessaoConsumoParticipante invited = new SessaoConsumoParticipante();
        invited.setId(1L);
        invited.setTenant(tenant);
        invited.setSessaoConsumo(sessao);
        invited.setStatus(SessaoParticipanteStatus.INVITED);
        invited.setExpiresAt(Instant.now().minusSeconds(60));

        SessaoConsumoParticipante pending = new SessaoConsumoParticipante();
        pending.setId(2L);
        pending.setTenant(tenant);
        pending.setSessaoConsumo(sessao);
        pending.setStatus(SessaoParticipanteStatus.PENDING_APPROVAL);
        pending.setExpiresAt(Instant.now().minusSeconds(60));

        when(repo.findExpiredCandidatesForUpdate(any(Instant.class), any(Pageable.class))).thenReturn(List.of(invited, pending));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessaoParticipanteExpirationService svc = new SessaoParticipanteExpirationService(repo, audit, props);
        var out = svc.expireOnce("batch-1");
        assertThat(out.expired()).isEqualTo(2);

        ArgumentCaptor<SessaoConsumoParticipante> cap = ArgumentCaptor.forClass(SessaoConsumoParticipante.class);
        Mockito.verify(repo, Mockito.atLeast(2)).save(cap.capture());
        assertThat(cap.getAllValues()).allSatisfy(p -> {
            assertThat(p.getStatus()).isEqualTo(SessaoParticipanteStatus.EXPIRED);
            assertThat(p.getExpiredAt()).isNotNull();
            assertThat(p.getCleanupBatchId()).isEqualTo("batch-1");
        });
    }
}

