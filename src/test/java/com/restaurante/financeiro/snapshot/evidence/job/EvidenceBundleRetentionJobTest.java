package com.restaurante.financeiro.snapshot.evidence.job;

import com.restaurante.financeiro.snapshot.evidence.service.EvidenceBundleRetentionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EvidenceBundleRetentionJobTest {

    @Test
    void run_calls_retention_service_with_scheduled_job_marker() {
        EvidenceBundleRetentionService service = mock(EvidenceBundleRetentionService.class);
        EvidenceBundleRetentionJob job = new EvidenceBundleRetentionJob(service);

        job.run();

        verify(service).runOnce("SCHEDULED_JOB");
    }
}

