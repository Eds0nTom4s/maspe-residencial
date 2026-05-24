package com.restaurante.fiscal.autoissue.worker;

import com.restaurante.fiscal.autoissue.repository.FiscalAutoIssueJobRepository;
import com.restaurante.fiscal.autoissue.service.FiscalAutoIssueFailureClassifier;
import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.service.FiscalDocumentService;
import com.restaurante.model.entity.FiscalAutoIssueJob;
import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class FiscalAutoIssueWorker {

    private final TaxProperties props;
    private final FiscalAutoIssueJobRepository jobRepository;
    private final FiscalDocumentService fiscalDocumentService;
    private final FiscalAutoIssueFailureClassifier classifier;
    private final OperationalEventLogService operationalEventLogService;

    @Scheduled(fixedDelayString = "${consuma.tax.document.auto-issue.worker-fixed-delay-ms:5000}")
    public void tick() {
        if (!props.isEnabled()) return;
        if (!props.getDocument().getAutoIssue().isEnabled()) return;

        recoverStaleLocks();

        LocalDateTime now = LocalDateTime.now();
        int batchSize = props.getDocument().getAutoIssue().getBatchSize();
        List<FiscalAutoIssueJob> due = jobRepository.findDueJobs(
                List.of(FiscalAutoIssueJobStatus.PENDING, FiscalAutoIssueJobStatus.FAILED_RETRYABLE),
                now,
                PageRequest.of(0, batchSize)
        );

        if (due.isEmpty()) return;
        for (FiscalAutoIssueJob j : due) {
            try {
                processOneClaiming(j.getId());
            } catch (Exception e) {
                log.warn("Falha ao processar job fiscal id={}: {}", j.getId(), e.getMessage());
            }
        }
    }

    private void recoverStaleLocks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusMinutes(props.getDocument().getAutoIssue().getStaleLockMinutes());
        List<Long> staleIds = jobRepository.findStaleProcessingJobIds(
                FiscalAutoIssueJobStatus.PROCESSING,
                staleCutoff,
                PageRequest.of(0, props.getDocument().getAutoIssue().getBatchSize())
        );
        for (Long id : staleIds) {
            try {
                recoverOneStaleLock(id, now);
            } catch (Exception e) {
                log.warn("Falha ao recuperar stale lock jobId={}: {}", id, e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverOneStaleLock(Long jobId, LocalDateTime now) {
        FiscalAutoIssueJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        if (job.getStatus() != FiscalAutoIssueJobStatus.PROCESSING) return;

        withTenantContext(job.getTenant().getId(), () -> {
            job.setStatus(FiscalAutoIssueJobStatus.FAILED_RETRYABLE);
            job.setLockedAt(null);
            job.setLockedBy(null);
            job.setErrorCode("STALE_LOCK");
            job.setErrorMessage("Stale lock recovered");
            job.setNextAttemptAt(now.plusSeconds(props.getDocument().getAutoIssue().getRetryBackoffSeconds()));
            jobRepository.save(job);

            operationalEventLogService.logGenericForTenant(
                    job.getTenant().getId(),
                    OperationalEventType.FISCAL_AUTO_ISSUE_STALE_LOCK_RECOVERED,
                    OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                    job.getId(),
                    OperationalOrigem.SYSTEM,
                    "Stale lock recovery aplicado ao job fiscal",
                    Map.of(
                            "jobId", job.getId(),
                            "attemptCount", job.getAttemptCount(),
                            "errorCode", job.getErrorCode()
                    ),
                    null,
                    null
            );
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOneClaiming(Long jobId) {
        if (jobId == null) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusMinutes(props.getDocument().getAutoIssue().getStaleLockMinutes());
        String workerId = props.getDocument().getAutoIssue().getWorkerId();

        int claimed = jobRepository.claimJob(
                jobId,
                now,
                workerId,
                staleCutoff,
                List.of(FiscalAutoIssueJobStatus.PENDING, FiscalAutoIssueJobStatus.FAILED_RETRYABLE)
        );
        if (claimed != 1) return;

        FiscalAutoIssueJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        withTenantContext(job.getTenant().getId(), () -> doProcess(job, workerId));
    }

    private void doProcess(FiscalAutoIssueJob job, String workerId) {
        if (job.getStatus() != FiscalAutoIssueJobStatus.PENDING && job.getStatus() != FiscalAutoIssueJobStatus.FAILED_RETRYABLE) {
            return;
        }

        job.setStatus(FiscalAutoIssueJobStatus.PROCESSING);
        job.setLastAttemptAt(LocalDateTime.now());
        job.setAttemptCount(job.getAttemptCount() + 1);
        jobRepository.save(job);

        operationalEventLogService.logGenericForTenant(
                job.getTenant().getId(),
                OperationalEventType.FISCAL_AUTO_ISSUE_JOB_PROCESSING_STARTED,
                OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Job fiscal em processamento",
                Map.of(
                        "jobId", job.getId(),
                        "attemptCount", job.getAttemptCount(),
                        "workerId", workerId
                ),
                null,
                null
        );

        try {
            var doc = fiscalDocumentService.issueForPedidoPaymentAsSystem(
                    job.getTenant().getId(),
                    job.getPedido().getId(),
                    job.getPagamento().getId()
            );
            job.setFiscalDocument(doc);
            job.setStatus(FiscalAutoIssueJobStatus.ISSUED);
            job.setProcessedAt(LocalDateTime.now());
            job.setErrorCode(null);
            job.setErrorMessage(null);
            jobRepository.save(job);

            operationalEventLogService.logGenericForTenant(
                    job.getTenant().getId(),
                    OperationalEventType.FISCAL_AUTO_ISSUE_JOB_ISSUED,
                    OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                    job.getId(),
                    OperationalOrigem.SYSTEM,
                    "Job fiscal concluído (documento emitido)",
                    Map.of(
                            "jobId", job.getId(),
                            "documentId", doc != null ? doc.getId() : null,
                            "pedidoId", job.getPedido().getId(),
                            "pagamentoId", job.getPagamento().getId()
                    ),
                    null,
                    null
            );
        } catch (Exception e) {
            var c = classifier.classify(e);
            boolean retryable = c.retryable() && job.getAttemptCount() < job.getMaxAttempts();

            job.setErrorCode(c.code());
            job.setErrorMessage(c.message());
            if (retryable) {
                job.setStatus(FiscalAutoIssueJobStatus.FAILED_RETRYABLE);
                job.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(job.getAttemptCount())));
                jobRepository.save(job);

                operationalEventLogService.logGenericForTenant(
                        job.getTenant().getId(),
                        OperationalEventType.FISCAL_AUTO_ISSUE_JOB_FAILED_RETRYABLE,
                        OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                        job.getId(),
                        OperationalOrigem.SYSTEM,
                        "Job fiscal falhou (retryable)",
                        Map.of(
                                "jobId", job.getId(),
                                "attemptCount", job.getAttemptCount(),
                                "errorCode", job.getErrorCode()
                        ),
                        null,
                        null
                );
            } else {
                job.setStatus(FiscalAutoIssueJobStatus.FAILED_PERMANENT);
                job.setNextAttemptAt(null);
                job.setProcessedAt(LocalDateTime.now());
                jobRepository.save(job);

                operationalEventLogService.logGenericForTenant(
                        job.getTenant().getId(),
                        OperationalEventType.FISCAL_AUTO_ISSUE_JOB_FAILED_PERMANENT,
                        OperationalEntityType.FISCAL_AUTO_ISSUE_JOB,
                        job.getId(),
                        OperationalOrigem.SYSTEM,
                        "Job fiscal falhou (permanente)",
                        Map.of(
                                "jobId", job.getId(),
                                "attemptCount", job.getAttemptCount(),
                                "errorCode", job.getErrorCode()
                        ),
                        null,
                        null
                );
            }
        } finally {
            job.setLockedAt(null);
            job.setLockedBy(null);
            jobRepository.save(job);
        }
    }

    private int backoffSeconds(int attemptCount) {
        int base = props.getDocument().getAutoIssue().getRetryBackoffSeconds();
        int max = props.getDocument().getAutoIssue().getMaxBackoffSeconds();
        long sec = (long) base * Math.max(1, attemptCount);
        return (int) Math.min(sec, max);
    }

    private void withTenantContext(Long tenantId, Runnable runnable) {
        TenantContextHolder.set(new TenantContext(
                tenantId,
                null,
                null,
                Set.of(),
                TenantResolutionSource.LEGACY_NONE,
                true,
                false
        ));
        try {
            runnable.run();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
