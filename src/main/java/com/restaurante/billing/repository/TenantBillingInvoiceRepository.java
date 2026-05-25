package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantBillingInvoiceRepository extends JpaRepository<TenantBillingInvoice, Long> {
    Optional<TenantBillingInvoice> findByTenantIdAndId(Long tenantId, Long id);
    Optional<TenantBillingInvoice> findByTenantIdAndBillingCycleId(Long tenantId, Long billingCycleId);
    List<TenantBillingInvoice> findByTenantIdOrderByIdDesc(Long tenantId);
}

