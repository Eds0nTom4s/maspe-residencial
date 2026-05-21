package com.restaurante.financeiro.snapshot.evidence.service;

import com.restaurante.financeiro.snapshot.evidence.EvidenceBundleProperties;
import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundleAccessLog;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleAccessLogRepository;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceBundleRetentionServiceTest {

    @Test
    void runOnce_marks_retention_expired_and_writes_access_log_and_operational_event() {
        TurnoEvidenceBundleRepository bundleRepo = mock(TurnoEvidenceBundleRepository.class);
        TurnoEvidenceBundleAccessLogRepository accessLogRepo = mock(TurnoEvidenceBundleAccessLogRepository.class);
        OperationalEventLogService eventLogService = mock(OperationalEventLogService.class);
        EvidenceBundleProperties props = new EvidenceBundleProperties();
        props.setRetentionJobBatchSize(10);

        EvidenceBundleRetentionService service = new EvidenceBundleRetentionService(
                bundleRepo,
                accessLogRepo,
                eventLogService,
                props
        );

        when(bundleRepo.lockExpiredIdsForRetention(anyInt())).thenReturn(List.of(10L, 11L));
        when(bundleRepo.markRetentionExpired(anyList(), any())).thenReturn(2);

        Tenant t1 = new Tenant();
        TurnoOperacional turno1 = new TurnoOperacional();
        turno1.setId(99L);
        var b1 = mock(com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundle.class);
        when(b1.getId()).thenReturn(10L);
        when(b1.getTenant()).thenReturn(t1);
        when(b1.getTurno()).thenReturn(turno1);
        when(b1.getSequenceNumber()).thenReturn(1);
        when(b1.getRetentionUntil()).thenReturn(LocalDateTime.now().minusDays(1));

        Tenant t2 = new Tenant();
        TurnoOperacional turno2 = new TurnoOperacional();
        turno2.setId(100L);
        var b2 = mock(com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundle.class);
        when(b2.getId()).thenReturn(11L);
        when(b2.getTenant()).thenReturn(t2);
        when(b2.getTurno()).thenReturn(turno2);
        when(b2.getSequenceNumber()).thenReturn(2);
        when(b2.getRetentionUntil()).thenReturn(LocalDateTime.now().minusDays(2));

        when(bundleRepo.findAllById(List.of(10L, 11L))).thenReturn(List.of(b1, b2));

        EvidenceBundleRetentionService.RetentionRunResult result = service.runOnce("TEST");

        assertThat(result.totalProcessados()).isEqualTo(2);
        assertThat(result.totalMarcadosExpirados()).isEqualTo(2);

        verify(accessLogRepo, times(2)).save(any(TurnoEvidenceBundleAccessLog.class));
        verify(eventLogService, times(2)).logTurnoEvent(
                any(),
                any(),
                any(),
                any(),
                anyMap(),
                eq(null),
                eq(null)
        );
    }

    @Test
    void runOnce_no_ids_is_noop() {
        TurnoEvidenceBundleRepository bundleRepo = mock(TurnoEvidenceBundleRepository.class);
        TurnoEvidenceBundleAccessLogRepository accessLogRepo = mock(TurnoEvidenceBundleAccessLogRepository.class);
        OperationalEventLogService eventLogService = mock(OperationalEventLogService.class);
        EvidenceBundleProperties props = new EvidenceBundleProperties();

        EvidenceBundleRetentionService service = new EvidenceBundleRetentionService(
                bundleRepo,
                accessLogRepo,
                eventLogService,
                props
        );

        when(bundleRepo.lockExpiredIdsForRetention(anyInt())).thenReturn(List.of());

        EvidenceBundleRetentionService.RetentionRunResult result = service.runOnce("TEST");

        assertThat(result.totalProcessados()).isEqualTo(0);
        assertThat(result.totalMarcadosExpirados()).isEqualTo(0);
    }
}
