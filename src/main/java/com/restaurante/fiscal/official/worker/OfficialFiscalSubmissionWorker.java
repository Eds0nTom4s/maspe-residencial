package com.restaurante.fiscal.official.worker;

import com.restaurante.fiscal.official.config.OfficialFiscalProperties;
import com.restaurante.fiscal.official.repository.OfficialFiscalSubmissionRepository;
import com.restaurante.fiscal.official.repository.TenantOfficialFiscalProfileRepository;
import com.restaurante.fiscal.official.service.OfficialFiscalSubmissionService;
import com.restaurante.model.enums.OfficialFiscalSubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfficialFiscalSubmissionWorker {

    private final OfficialFiscalProperties props;
    private final TenantOfficialFiscalProfileRepository profileRepository;
    private final OfficialFiscalSubmissionRepository submissionRepository;
    private final OfficialFiscalSubmissionService submissionService;

    @Scheduled(fixedDelayString = "${consuma.fiscal.official.worker-fixed-delay-ms:5000}")
    public void tick() {
        if (!props.isEnabled()) return;
        if (!props.isWorkerEnabled()) return;

        LocalDateTime now = LocalDateTime.now();
        List<Long> tenantIds = profileRepository.listOfficialEnabledTenantIds();
        if (tenantIds == null || tenantIds.isEmpty()) return;

        for (Long tenantId : tenantIds) {
            try {
                processTenantBatch(tenantId, now);
            } catch (Exception e) {
                log.warn("Falha no worker official fiscal tenantId={}: {}", tenantId, e.getMessage());
            }
        }
    }

    private void processTenantBatch(Long tenantId, LocalDateTime now) {
        if (tenantId == null) return;
        List<Long> dueIds = submissionRepository.findRunnable(
                        tenantId,
                        List.of(OfficialFiscalSubmissionStatus.DRAFT, OfficialFiscalSubmissionStatus.FAILED_RETRYABLE, OfficialFiscalSubmissionStatus.PENDING_SUBMISSION),
                        now,
                        PageRequest.of(0, props.getBatchSize())
                )
                .stream()
                .map(s -> s.getId())
                .toList();

        if (dueIds.isEmpty()) return;
        for (Long id : dueIds) {
            try {
                submissionService.processOneClaiming(tenantId, id);
            } catch (Exception e) {
                log.warn("Falha ao processar submissão official tenantId={} submissionId={}: {}", tenantId, id, e.getMessage());
            }
        }
    }
}

