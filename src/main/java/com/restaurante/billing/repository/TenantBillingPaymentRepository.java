package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingPayment;
import com.restaurante.model.enums.TenantBillingPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TenantBillingPaymentRepository extends JpaRepository<TenantBillingPayment, Long> {
    Optional<TenantBillingPayment> findByTenantIdAndId(Long tenantId, Long id);
    Page<TenantBillingPayment> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
    List<TenantBillingPayment> findByTenantIdAndInvoice_IdOrderByIdAsc(Long tenantId, Long invoiceId);
    List<TenantBillingPayment> findByTenantIdAndInvoice_IdAndStatusInOrderByIdAsc(Long tenantId, Long invoiceId, java.util.Collection<TenantBillingPaymentStatus> statuses);
}

