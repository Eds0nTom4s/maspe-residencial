package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantBillingPaymentAttemptRepository extends JpaRepository<TenantBillingPaymentAttempt, Long> {
    Optional<TenantBillingPaymentAttempt> findByTenantIdAndId(Long tenantId, Long id);
    List<TenantBillingPaymentAttempt> findByTenantIdAndInvoice_IdOrderByAttemptedAtDesc(Long tenantId, Long invoiceId);
}

