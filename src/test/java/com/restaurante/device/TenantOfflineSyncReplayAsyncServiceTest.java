package com.restaurante.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.config.DeviceOfflineReplayAsyncProperties;
import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import com.restaurante.device.offline.entity.DeviceOfflineSyncSession;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.device.offline.repository.DeviceOfflineReplayOperationItemRepository;
import com.restaurante.device.offline.repository.DeviceOfflineReplayOperationRepository;
import com.restaurante.device.offline.repository.DeviceOfflineSyncSessionRepository;
import com.restaurante.dto.response.OfflineCommandReplayEligibilityResponse;
import com.restaurante.dto.response.OfflineReplayAsyncSubmitResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityReason;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityStatus;
import com.restaurante.model.enums.DeviceOfflineReplayOperationStatus;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.service.tenant.offline.DeviceOfflineReplayEligibilityService;
import com.restaurante.service.tenant.offline.TenantOfflineSyncReplayAsyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TenantOfflineSyncReplayAsyncServiceTest {

    @Test
    void submit_is_rate_limited_by_active_ops() {
        TenantGuard guard = Mockito.mock(TenantGuard.class);
        DeviceOfflineReplayAsyncProperties props = new DeviceOfflineReplayAsyncProperties();
        props.setMaxActiveOperationsPerTenant(1);

        DeviceOfflineSyncSessionRepository sessionRepo = Mockito.mock(DeviceOfflineSyncSessionRepository.class);
        DeviceOfflineCommandRepository cmdRepo = Mockito.mock(DeviceOfflineCommandRepository.class);
        DeviceOfflineReplayOperationRepository opRepo = Mockito.mock(DeviceOfflineReplayOperationRepository.class);
        DeviceOfflineReplayOperationItemRepository itemRepo = Mockito.mock(DeviceOfflineReplayOperationItemRepository.class);
        DeviceOfflineReplayEligibilityService eligibility = Mockito.mock(DeviceOfflineReplayEligibilityService.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);

        TenantContext ctx = new TenantContext(10L, "t-10", 99L, Set.of("TENANT_OWNER"), com.restaurante.security.tenant.TenantResolutionSource.JWT, false, false);
        when(guard.requireContext()).thenReturn(ctx);

        Tenant t = new Tenant();
        t.setId(10L);
        DeviceOfflineSyncSession session = new DeviceOfflineSyncSession();
        session.setId(1L);
        session.setTenant(t);
        session.setServerSyncId("S1");
        session.setReceivedAt(Instant.now());
        session.setUnidadeAtendimento(new UnidadeAtendimento());
        DispositivoOperacional d = new DispositivoOperacional();
        d.setId(777L);
        session.setDispositivoOperacional(d);
        when(sessionRepo.findByTenantIdAndServerSyncId(10L, "S1")).thenReturn(Optional.of(session));

        when(opRepo.countByTenantAndStatuses(any(), any())).thenReturn(1L);

        TenantOfflineSyncReplayAsyncService svc = new TenantOfflineSyncReplayAsyncService(
                guard, props, new ObjectMapper(),
                sessionRepo, cmdRepo, opRepo, itemRepo,
                eligibility, audit
        );

        assertThatThrownBy(() -> svc.submit("S1", null, List.of(DeviceOfflineCommandStatus.FAILED), null, "reason", false, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OFFLINE_REPLAY_RATE_LIMITED");
    }

    @Test
    void submit_creates_operation_only_for_eligible_commands() {
        TenantGuard guard = Mockito.mock(TenantGuard.class);
        DeviceOfflineReplayAsyncProperties props = new DeviceOfflineReplayAsyncProperties();
        props.setMaxItemsPerOperation(100);

        DeviceOfflineSyncSessionRepository sessionRepo = Mockito.mock(DeviceOfflineSyncSessionRepository.class);
        DeviceOfflineCommandRepository cmdRepo = Mockito.mock(DeviceOfflineCommandRepository.class);
        DeviceOfflineReplayOperationRepository opRepo = Mockito.mock(DeviceOfflineReplayOperationRepository.class);
        DeviceOfflineReplayOperationItemRepository itemRepo = Mockito.mock(DeviceOfflineReplayOperationItemRepository.class);
        DeviceOfflineReplayEligibilityService eligibilityService = Mockito.mock(DeviceOfflineReplayEligibilityService.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);

        TenantContext ctx = new TenantContext(10L, "t-10", 99L, Set.of("TENANT_OWNER"), com.restaurante.security.tenant.TenantResolutionSource.JWT, false, false);
        when(guard.requireContext()).thenReturn(ctx);

        Tenant t = new Tenant();
        t.setId(10L);
        DeviceOfflineSyncSession session = new DeviceOfflineSyncSession();
        session.setId(1L);
        session.setTenant(t);
        session.setServerSyncId("S1");
        session.setReceivedAt(Instant.now());
        session.setUnidadeAtendimento(new UnidadeAtendimento());
        DispositivoOperacional d = new DispositivoOperacional();
        d.setId(777L);
        session.setDispositivoOperacional(d);
        when(sessionRepo.findByTenantIdAndServerSyncId(10L, "S1")).thenReturn(Optional.of(session));

        when(opRepo.countByTenantAndStatuses(any(), any())).thenReturn(0L);
        when(opRepo.countRequestedSince(any(), any())).thenReturn(0L);
        when(opRepo.existsByTenant_IdAndServerSyncIdAndStatusIn(any(), any(), any())).thenReturn(false);

        DeviceOfflineCommand eligible = new DeviceOfflineCommand();
        eligible.setId(10L);
        eligible.setTenant(t);
        eligible.setServerSyncId("S1");
        eligible.setClientRequestId("cmd-1");
        eligible.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        eligible.setStatus(DeviceOfflineCommandStatus.FAILED);

        DeviceOfflineCommand notEligible = new DeviceOfflineCommand();
        notEligible.setId(11L);
        notEligible.setTenant(t);
        notEligible.setServerSyncId("S1");
        notEligible.setClientRequestId("cmd-2");
        notEligible.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        notEligible.setStatus(DeviceOfflineCommandStatus.APPLIED);

        when(cmdRepo.listForReplay(any(), any(), any(), any(Boolean.class), any(), any(Boolean.class)))
                .thenReturn(List.of(eligible, notEligible));

        OfflineCommandReplayEligibilityResponse e1 = new OfflineCommandReplayEligibilityResponse();
        e1.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.ELIGIBLE);
        e1.setReason(DeviceOfflineReplayEligibilityReason.ELIGIBLE_RETRYABLE_FAILURE);
        e1.setEligible(true);

        OfflineCommandReplayEligibilityResponse e2 = new OfflineCommandReplayEligibilityResponse();
        e2.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.NOT_ELIGIBLE);
        e2.setReason(DeviceOfflineReplayEligibilityReason.COMMAND_ALREADY_APPLIED);
        e2.setEligible(false);

        when(eligibilityService.evaluate(eligible, false)).thenReturn(e1);
        when(eligibilityService.evaluate(notEligible, false)).thenReturn(e2);

        when(cmdRepo.markReplayInProgress(any(), any(), any())).thenReturn(1);

        ArgumentCaptor<com.restaurante.device.offline.entity.DeviceOfflineReplayOperation> opCaptor =
                ArgumentCaptor.forClass(com.restaurante.device.offline.entity.DeviceOfflineReplayOperation.class);
        when(opRepo.save(opCaptor.capture())).thenAnswer(inv -> {
            var op = opCaptor.getValue();
            op.setId(123L);
            return op;
        });

        TenantOfflineSyncReplayAsyncService svc = new TenantOfflineSyncReplayAsyncService(
                guard, props, new ObjectMapper(),
                sessionRepo, cmdRepo, opRepo, itemRepo,
                eligibilityService, audit
        );

        OfflineReplayAsyncSubmitResponse out = svc.submit(
                "S1",
                null,
                List.of(DeviceOfflineCommandStatus.FAILED),
                null,
                "support-replay",
                false,
                null,
                null
        );

        assertThat(out.getServerSyncId()).isEqualTo("S1");
        assertThat(out.getStatus()).isEqualTo(DeviceOfflineReplayOperationStatus.PENDING);
        assertThat(out.getTotalItems()).isEqualTo(1);
        assertThat(out.getOperationId()).isNotBlank();
    }
}

