package com.restaurante.billing.repository;

import com.restaurante.model.entity.UsageAdjustment;
import com.restaurante.model.enums.UsageMetricCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsageAdjustmentRepository extends JpaRepository<UsageAdjustment, Long> {
    List<UsageAdjustment> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<UsageAdjustment> findByTenantIdAndMetricCodeOrderByCreatedAtDesc(Long tenantId, UsageMetricCode metricCode);
}
