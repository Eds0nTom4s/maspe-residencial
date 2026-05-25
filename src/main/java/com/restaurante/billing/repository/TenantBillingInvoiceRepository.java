package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface TenantBillingInvoiceRepository extends JpaRepository<TenantBillingInvoice, Long> {
    Optional<TenantBillingInvoice> findByTenantIdAndId(Long tenantId, Long id);
    Optional<TenantBillingInvoice> findByTenantIdAndBillingCycleId(Long tenantId, Long billingCycleId);
    List<TenantBillingInvoice> findByTenantIdOrderByIdDesc(Long tenantId);

    @Query("""
            select i from TenantBillingInvoice i
            where i.tenant.id = :tenantId
              and i.status in :statuses
              and i.dueAt is not null
              and i.dueAt < :now
              and i.outstandingAmount > 0
            order by i.dueAt asc, i.id asc
            """)
    List<TenantBillingInvoice> findOverdueCandidates(@Param("tenantId") Long tenantId,
                                                     @Param("statuses") java.util.Collection<com.restaurante.model.enums.TenantBillingInvoiceStatus> statuses,
                                                     @Param("now") LocalDateTime now);
}
