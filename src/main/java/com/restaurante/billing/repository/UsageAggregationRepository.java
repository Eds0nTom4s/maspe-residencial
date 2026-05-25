package com.restaurante.billing.repository;

import com.restaurante.model.entity.UsageAggregation;
import com.restaurante.model.enums.UsageAggregationStatus;
import com.restaurante.model.enums.UsageMetricCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UsageAggregationRepository extends JpaRepository<UsageAggregation, Long> {
    @Query("""
            select a from UsageAggregation a
            where a.tenant.id = :tenantId
              and a.subscription.id = :subscriptionId
              and a.metricCode = :metric
              and a.periodStart = :start
              and a.periodEnd = :end
            order by a.id desc
            """)
    Optional<UsageAggregation> findLatestForPeriod(@Param("tenantId") Long tenantId,
                                                  @Param("subscriptionId") Long subscriptionId,
                                                  @Param("metric") UsageMetricCode metric,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    List<UsageAggregation> findByTenantIdAndBillingCycleIdOrderByMetricCodeAsc(Long tenantId, Long billingCycleId);

    List<UsageAggregation> findByTenantIdAndStatusOrderByIdDesc(Long tenantId, UsageAggregationStatus status);
}

