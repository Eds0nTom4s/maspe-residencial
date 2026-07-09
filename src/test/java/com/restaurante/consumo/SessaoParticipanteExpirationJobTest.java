package com.restaurante.consumo;

import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import com.restaurante.consumo.participante.entity.SessaoParticipanteLifecycleJobRun;
import com.restaurante.consumo.participante.job.SessaoParticipanteExpirationJob;
import com.restaurante.consumo.participante.repository.SessaoParticipanteLifecycleJobRunRepository;
import com.restaurante.consumo.participante.service.SessaoParticipanteExpirationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prompt 41.3 — Testes unitários do SessaoParticipanteExpirationJob.
 * Actualizado (41.4) para incluir o novo parâmetro jobRunRepository.
 */
class SessaoParticipanteExpirationJobTest {

    private SessaoParticipanteLifecycleJobRunRepository mockRepo() {
        SessaoParticipanteLifecycleJobRunRepository repo = Mockito.mock(SessaoParticipanteLifecycleJobRunRepository.class);
        var run = new SessaoParticipanteLifecycleJobRun();
        run.setId(1L);
        when(repo.save(any())).thenReturn(run);
        return repo;
    }

    @Test
    void job_enabled_calls_expiration_service() {
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        SessaoParticipanteExpirationService svc = Mockito.mock(SessaoParticipanteExpirationService.class);
        when(svc.expireOnce(anyString())).thenReturn(
                new SessaoParticipanteExpirationService.ExpirationRunResult("batch-x", 2, 2)
        );

        SessaoParticipanteExpirationJob job = new SessaoParticipanteExpirationJob(props, svc, mockRepo());
        job.run();

        verify(svc, times(1)).expireOnce(anyString());
    }

    @Test
    void job_disabled_does_not_call_service() {
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(false);

        SessaoParticipanteExpirationService svc = Mockito.mock(SessaoParticipanteExpirationService.class);
        SessaoParticipanteLifecycleJobRunRepository repo = Mockito.mock(SessaoParticipanteLifecycleJobRunRepository.class);

        SessaoParticipanteExpirationJob job = new SessaoParticipanteExpirationJob(props, svc, repo);
        job.run();

        verify(svc, never()).expireOnce(anyString());
        verify(repo, never()).save(any());
    }

    @Test
    void job_handles_service_exception_without_propagating() {
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        SessaoParticipanteExpirationService svc = Mockito.mock(SessaoParticipanteExpirationService.class);
        when(svc.expireOnce(anyString())).thenThrow(new RuntimeException("DB connection lost"));

        SessaoParticipanteExpirationJob job = new SessaoParticipanteExpirationJob(props, svc, mockRepo());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(job::run);
    }

    @Test
    void job_enabled_with_zero_expired_still_runs() {
        SessaoParticipanteLifecycleProperties props = new SessaoParticipanteLifecycleProperties();
        props.setExpirationJobEnabled(true);

        SessaoParticipanteExpirationService svc = Mockito.mock(SessaoParticipanteExpirationService.class);
        when(svc.expireOnce(anyString())).thenReturn(
                new SessaoParticipanteExpirationService.ExpirationRunResult("batch-y", 0, 0)
        );

        SessaoParticipanteExpirationJob job = new SessaoParticipanteExpirationJob(props, svc, mockRepo());
        job.run();

        verify(svc, times(1)).expireOnce(anyString());
    }
}
