package com.restaurante.consumo.participante.job;

import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import com.restaurante.consumo.participante.entity.SessaoParticipanteLifecycleJobRun;
import com.restaurante.consumo.participante.repository.SessaoParticipanteLifecycleJobRunRepository;
import com.restaurante.consumo.participante.service.SessaoParticipanteExpirationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Prompt 41.3/41.4 — Job de expiração de participantes pendentes.
 * Prompt 41.4 adiciona: registo de runs para health check/observabilidade.
 */
@Component
@RequiredArgsConstructor
public class SessaoParticipanteExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(SessaoParticipanteExpirationJob.class);
    public static final String JOB_NAME = "SessaoParticipanteExpirationJob";

    private final SessaoParticipanteLifecycleProperties props;
    private final SessaoParticipanteExpirationService expirationService;
    private final SessaoParticipanteLifecycleJobRunRepository jobRunRepository;

    @Scheduled(cron = "${consuma.sessao.participantes.expiration-job-cron:0 */1 * * * *}")
    public void run() {
        if (!props.isExpirationJobEnabled()) return;

        String batchId = "SP-EXP-" + UUID.randomUUID();
        Instant startedAt = Instant.now();

        SessaoParticipanteLifecycleJobRun jobRun = createRunRecord(batchId, startedAt);

        try {
            var result = expirationService.expireOnce(batchId);

            jobRun.setFinishedAt(Instant.now());
            jobRun.setStatus("SUCCESS");
            jobRun.setScannedCount(result.candidates());
            jobRun.setExpiredCount(result.expired());

            if (result.expired() > 0) {
                log.info("[{}] batchId={} candidates={} expired={}", JOB_NAME, batchId, result.candidates(), result.expired());
            } else {
                log.debug("[{}] batchId={} candidates={} expired={}", JOB_NAME, batchId, result.candidates(), result.expired());
            }
        } catch (Exception e) {
            log.error("[{}] batchId={}: {}", JOB_NAME, batchId, e.getMessage(), e);
            jobRun.setFinishedAt(Instant.now());
            jobRun.setStatus("FAILED");
            jobRun.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 2000
                    ? e.getMessage().substring(0, 2000) : e.getMessage());
        } finally {
            // Não deve propagar exceção do save de auditoria
            try {
                jobRunRepository.save(jobRun);
            } catch (Exception auditEx) {
                log.warn("[{}] Falha ao persistir job run (não crítico): {}", JOB_NAME, auditEx.getMessage());
            }
        }
    }

    private SessaoParticipanteLifecycleJobRun createRunRecord(String batchId, Instant startedAt) {
        SessaoParticipanteLifecycleJobRun run = new SessaoParticipanteLifecycleJobRun();
        run.setJobName(JOB_NAME);
        run.setBatchId(batchId);
        run.setStartedAt(startedAt);
        run.setStatus("RUNNING");
        try {
            return jobRunRepository.save(run);
        } catch (Exception e) {
            log.warn("[{}] Falha ao criar job run record (não crítico): {}", JOB_NAME, e.getMessage());
            return run; // retorna sem id — save posterior pode falhar mas não quebra job
        }
    }
}
