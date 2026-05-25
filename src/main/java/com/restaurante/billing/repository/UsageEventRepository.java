package com.restaurante.billing.repository;

import com.restaurante.model.entity.UsageEvent;
import com.restaurante.model.enums.UsageEventStatus;
import com.restaurante.model.enums.UsageMetricCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {
    Optional<UsageEvent> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);
    List<UsageEvent> findByTenantIdOrderByOccurredAtDesc(Long tenantId);

    @Query("""
            select e from UsageEvent e
            where e.tenant.id = :tenantId
              and e.status = :status
              and e.occurredAt >= :start
              and e.occurredAt < :end
            """)
    List<UsageEvent> findRecordedInPeriod(@Param("tenantId") Long tenantId,
                                         @Param("status") UsageEventStatus status,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    @Query("""
            select e from UsageEvent e
            where e.tenant.id = :tenantId
              and e.status = :status
              and e.metricCode = :metric
              and e.occurredAt >= :start
              and e.occurredAt < :end
            """)
    List<UsageEvent> findRecordedInPeriodByMetric(@Param("tenantId") Long tenantId,
                                                 @Param("status") UsageEventStatus status,
                                                 @Param("metric") UsageMetricCode metric,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}
