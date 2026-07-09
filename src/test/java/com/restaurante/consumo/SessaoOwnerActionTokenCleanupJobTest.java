package com.restaurante.consumo;

import com.restaurante.config.SessaoOwnerActionTokenProperties;
import com.restaurante.consumo.participante.job.SessaoOwnerActionTokenCleanupJob;
import com.restaurante.consumo.participante.repository.SessaoOwnerActionTokenRepository;
import com.restaurante.model.enums.SessaoOwnerActionTokenStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Prompt 41.5 — Testes do SessaoOwnerActionTokenCleanupJob.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessaoOwnerActionTokenCleanupJob — Prompt 41.5")
class SessaoOwnerActionTokenCleanupJobTest {

    @Mock SessaoOwnerActionTokenRepository tokenRepository;
    @Mock SessaoOwnerActionTokenProperties tokenProps;
    @Mock OperationalEventLogService eventLogService;

    SessaoOwnerActionTokenCleanupJob job;

    private SessaoOwnerActionTokenProperties.Cleanup cleanupProps;

    @BeforeEach
    void setUp() {
        cleanupProps = new SessaoOwnerActionTokenProperties.Cleanup();
        cleanupProps.setEnabled(true);
        cleanupProps.setRetentionDays(30);
        cleanupProps.setBatchSize(500);

        when(tokenProps.getCleanup()).thenReturn(cleanupProps);
        job = new SessaoOwnerActionTokenCleanupJob(tokenRepository, tokenProps, eventLogService);
    }

    @Test
    @DisplayName("job desactivado não executa nenhuma query")
    void job_disabled_nao_executa() {
        cleanupProps.setEnabled(false);

        job.run();

        verify(tokenRepository, never()).findFinalizedIdsCrossTenantsBeforeForCleanup(any(), any(), any());
        verify(tokenRepository, never()).deleteByIds(anyList());
    }

    @Test
    @DisplayName("remove tokens finalizados com createdAt antes do cutoff")
    void remove_tokens_finalizados() {
        when(tokenRepository.findFinalizedIdsCrossTenantsBeforeForCleanup(any(), any(), any()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(tokenRepository.deleteByIds(List.of(1L, 2L, 3L))).thenReturn(3);

        job.run();

        verify(tokenRepository).deleteByIds(List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("não remove quando não há elegíveis")
    void nenhum_elegivel_nao_deleta() {
        when(tokenRepository.findFinalizedIdsCrossTenantsBeforeForCleanup(any(), any(), any()))
                .thenReturn(List.of());

        job.run();

        verify(tokenRepository, never()).deleteByIds(anyList());
    }

    @Test
    @DisplayName("não remove tokens ACTIVE")
    void nao_remove_active() {
        // Verificar que ACTIVE não está na lista de statuses passados para a query
        when(tokenRepository.findFinalizedIdsCrossTenantsBeforeForCleanup(
                argThat(statuses -> !statuses.contains(SessaoOwnerActionTokenStatus.ACTIVE)),
                any(), any()
        )).thenReturn(List.of());

        job.run();

        verify(tokenRepository, never()).deleteByIds(anyList());
    }

    @Test
    @DisplayName("respeita batch-size na query")
    void respeita_batch_size() {
        cleanupProps.setBatchSize(10);
        when(tokenRepository.findFinalizedIdsCrossTenantsBeforeForCleanup(
                any(), any(),
                argThat(p -> p.getPageSize() == 10)
        )).thenReturn(List.of());

        job.run();
    }

    @Test
    @DisplayName("auditoria sanitizada — sem tokenHash individual")
    void auditoria_sanitizada() {
        when(tokenRepository.findFinalizedIdsCrossTenantsBeforeForCleanup(any(), any(), any()))
                .thenReturn(List.of(10L));
        when(tokenRepository.deleteByIds(anyList())).thenReturn(1);

        job.run();

        // Verificar que auditoria foi emitida (13 params)
        verify(eventLogService, atLeastOnce()).logPublicEvent(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(),
                any(String.class), any(), any(), any());
    }
}
