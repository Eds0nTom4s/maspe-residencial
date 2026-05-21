package com.restaurante.financeiro.snapshot.evidence.job;

import com.restaurante.financeiro.snapshot.evidence.service.EvidenceBundleRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "consuma.financeiro.evidence-bundle",
        name = "retention-job-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EvidenceBundleRetentionJob {

    private final EvidenceBundleRetentionService retentionService;

    @Scheduled(cron = "${consuma.financeiro.evidence-bundle.retention-job-cron:0 0 3 * * *}")
    public void run() {
        retentionService.runOnce("SCHEDULED_JOB");
    }
}

