package com.restaurante.billing.repository;

import com.restaurante.model.entity.BillingCycle;
import com.restaurante.model.enums.BillingCycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BillingCycleRepository extends JpaRepository<BillingCycle, Long> {
    Optional<BillingCycle> findByTenantIdAndId(Long tenantId, Long id);
    Page<BillingCycle> findByTenantIdOrderByPeriodStartDesc(Long tenantId, Pageable pageable);

    @Query("""
            select c from BillingCycle c
            where c.tenant.id = :tenantId
              and c.subscription.id = :subscriptionId
              and c.status in :statuses
              and c.periodStart <= :at
              and c.periodEnd > :at
            order by c.id desc
            """)
    Optional<BillingCycle> findCurrent(@Param("tenantId") Long tenantId,
                                       @Param("subscriptionId") Long subscriptionId,
                                       @Param("statuses") java.util.Set<BillingCycleStatus> statuses,
                                       @Param("at") LocalDateTime at);
}
