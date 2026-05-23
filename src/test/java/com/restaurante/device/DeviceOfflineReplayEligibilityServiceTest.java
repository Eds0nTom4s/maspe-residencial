package com.restaurante.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.dto.response.OfflineCommandReplayEligibilityResponse;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DeviceOfflineConflictCode;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityReason;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityStatus;
import com.restaurante.service.tenant.offline.DeviceOfflineReplayEligibilityService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DeviceOfflineReplayEligibilityServiceTest {

    @Test
    void applied_is_not_eligible() {
        DeviceOfflineReplayEligibilityService svc = new DeviceOfflineReplayEligibilityService(
                new ObjectMapper(),
                Mockito.mock(DeviceOfflineCommandRepository.class),
                Mockito.mock(OrdemPagamentoRepository.class)
        );

        DeviceOfflineCommand cmd = new DeviceOfflineCommand();
        cmd.setId(1L);
        cmd.setClientRequestId("cmd-1");
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd.setStatus(DeviceOfflineCommandStatus.APPLIED);

        OfflineCommandReplayEligibilityResponse r = svc.evaluate(cmd, true);
        assertThat(r.isEligible()).isFalse();
        assertThat(r.getEligibilityStatus()).isEqualTo(DeviceOfflineReplayEligibilityStatus.NOT_ELIGIBLE);
        assertThat(r.getReason()).isEqualTo(DeviceOfflineReplayEligibilityReason.COMMAND_ALREADY_APPLIED);
    }

    @Test
    void idempotency_conflict_is_not_eligible() {
        DeviceOfflineReplayEligibilityService svc = new DeviceOfflineReplayEligibilityService(
                new ObjectMapper(),
                Mockito.mock(DeviceOfflineCommandRepository.class),
                Mockito.mock(OrdemPagamentoRepository.class)
        );

        DeviceOfflineCommand cmd = new DeviceOfflineCommand();
        cmd.setId(2L);
        cmd.setClientRequestId("cmd-2");
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd.setStatus(DeviceOfflineCommandStatus.CONFLICT);
        cmd.setConflictCode(DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name());

        OfflineCommandReplayEligibilityResponse r = svc.evaluate(cmd, true);
        assertThat(r.isEligible()).isFalse();
        assertThat(r.getReason()).isEqualTo(DeviceOfflineReplayEligibilityReason.IDEMPOTENCY_CONFLICT_NOT_REPLAYABLE);
    }

    @Test
    void conflict_retryable_without_created_entity_is_eligible() {
        DeviceOfflineCommandRepository cmdRepo = Mockito.mock(DeviceOfflineCommandRepository.class);
        DeviceOfflineReplayEligibilityService svc = new DeviceOfflineReplayEligibilityService(
                new ObjectMapper(),
                cmdRepo,
                Mockito.mock(OrdemPagamentoRepository.class)
        );

        Tenant t = new Tenant();
        t.setId(10L);
        DispositivoOperacional d = new DispositivoOperacional();
        d.setId(20L);

        DeviceOfflineCommand cmd = new DeviceOfflineCommand();
        cmd.setId(3L);
        cmd.setTenant(t);
        cmd.setDispositivoOperacional(d);
        cmd.setClientRequestId("cmd-3");
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd.setStatus(DeviceOfflineCommandStatus.CONFLICT);
        cmd.setConflictCode(DeviceOfflineConflictCode.PRODUCT_INACTIVE.name());

        when(cmdRepo.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(any(), any(), any()))
                .thenReturn(Optional.empty());

        OfflineCommandReplayEligibilityResponse r = svc.evaluate(cmd, true);
        assertThat(r.isEligible()).isTrue();
        assertThat(r.getEligibilityStatus()).isEqualTo(DeviceOfflineReplayEligibilityStatus.ELIGIBLE);
        assertThat(r.getReason()).isEqualTo(DeviceOfflineReplayEligibilityReason.ELIGIBLE_RETRYABLE_FAILURE);
    }
}

