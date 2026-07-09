package com.restaurante.service.operacional;

import com.restaurante.exception.BusinessException;
import com.restaurante.repository.OperationalEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationalEventRetentionService {

    private final OperationalEventLogRepository repository;

    @Value("${consuma.operational-events.retention-days:180}")
    private int retentionDays;

    @Value("${consuma.operational-events.cleanup-enabled:false}")
    private boolean cleanupEnabled;

    @Transactional(readOnly = true)
    public long countOldEvents(Long tenantId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return repository.countByTenantIdAndCreatedAtBefore(tenantId, cutoff);
    }

    @Transactional
    public int cleanupOldEvents(Long tenantId) {
        if (!cleanupEnabled) {
            throw new BusinessException("Cleanup de eventos operacionais está desabilitado.");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return repository.deleteByTenantIdAndCreatedAtBefore(tenantId, cutoff);
    }
}

