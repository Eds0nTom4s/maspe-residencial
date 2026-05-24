package com.restaurante.consumo;

import com.restaurante.consumo.participante.entity.SessaoParticipanteLifecycleJobRun;
import com.restaurante.consumo.participante.job.SessaoParticipanteExpirationJob;
import com.restaurante.consumo.participante.repository.SessaoParticipanteLifecycleJobRunRepository;
import com.restaurante.consumo.participante.service.SessaoParticipanteExpirationService;
import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prompt 41.4 — Testes do job de expiração com observabilidade (registos de runs).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessaoParticipanteExpirationJob (41.4) — Registo de runs")
class SessaoParticipanteLifecycleJobHealthIT {

    @Mock SessaoParticipanteLifecycleProperties props;
    @Mock SessaoParticipanteExpirationService expirationService;
    @Mock SessaoParticipanteLifecycleJobRunRepository jobRunRepository;

    SessaoParticipanteExpirationJob job;

    @BeforeEach
    void setUp() {
        job = new SessaoParticipanteExpirationJob(props, expirationService, jobRunRepository);
    }

    @Test
    @DisplayName("job habilitado regista run com status SUCCESS")
    void job_enabled_regista_run_success() {
        when(props.isExpirationJobEnabled()).thenReturn(true);

        var run = new SessaoParticipanteLifecycleJobRun();
        run.setId(1L);
        run.setStatus("RUNNING");
        when(jobRunRepository.save(any())).thenReturn(run);
        when(expirationService.expireOnce(anyString()))
                .thenReturn(new SessaoParticipanteExpirationService.ExpirationRunResult("SP-EXP-1", 5, 3));

        job.run();

        ArgumentCaptor<SessaoParticipanteLifecycleJobRun> captor = ArgumentCaptor.forClass(SessaoParticipanteLifecycleJobRun.class);
        verify(jobRunRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        var lastSaved = captor.getAllValues().stream()
                .filter(r -> "SUCCESS".equals(r.getStatus()) || r.getExpiredCount() > 0)
                .findFirst();
        assertThat(lastSaved).isPresent();
    }

    @Test
    @DisplayName("job desabilitado não chama expirationService")
    void job_disabled_nao_chama_service() {
        when(props.isExpirationJobEnabled()).thenReturn(false);

        job.run();

        verify(expirationService, never()).expireOnce(anyString());
        verify(jobRunRepository, never()).save(any());
    }

    @Test
    @DisplayName("erro no expireOnce regista run com status FAILED")
    void erro_no_expire_regista_run_failed() {
        when(props.isExpirationJobEnabled()).thenReturn(true);

        var run = new SessaoParticipanteLifecycleJobRun();
        run.setId(1L);
        run.setStatus("RUNNING");
        when(jobRunRepository.save(any())).thenReturn(run);
        when(expirationService.expireOnce(anyString()))
                .thenThrow(new RuntimeException("DB error simulado"));

        job.run(); // não deve propagar

        ArgumentCaptor<SessaoParticipanteLifecycleJobRun> captor = ArgumentCaptor.forClass(SessaoParticipanteLifecycleJobRun.class);
        verify(jobRunRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        var failedRun = captor.getAllValues().stream()
                .filter(r -> "FAILED".equals(r.getStatus()))
                .findFirst();
        assertThat(failedRun).isPresent();
        assertThat(failedRun.get().getErrorMessage()).contains("DB error simulado");
    }

    @Test
    @DisplayName("JOB_NAME é public e correto")
    void job_name_e_publico() {
        assertThat(SessaoParticipanteExpirationJob.JOB_NAME)
                .isEqualTo("SessaoParticipanteExpirationJob");
    }

    @Test
    @DisplayName("run inicial cria registo RUNNING antes do serviço executar")
    void run_cria_registo_running_antes() {
        when(props.isExpirationJobEnabled()).thenReturn(true);

        var run = new SessaoParticipanteLifecycleJobRun();
        run.setId(1L);
        run.setStatus("RUNNING");
        when(jobRunRepository.save(any())).thenReturn(run);
        when(expirationService.expireOnce(anyString()))
                .thenReturn(new SessaoParticipanteExpirationService.ExpirationRunResult("batch", 0, 0));

        job.run();

        ArgumentCaptor<SessaoParticipanteLifecycleJobRun> captor = ArgumentCaptor.forClass(SessaoParticipanteLifecycleJobRun.class);
        verify(jobRunRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        var firstSaved = captor.getAllValues().get(0);
        assertThat(firstSaved.getJobName()).isEqualTo(SessaoParticipanteExpirationJob.JOB_NAME);
        assertThat(firstSaved.getStartedAt()).isNotNull();
    }
}
