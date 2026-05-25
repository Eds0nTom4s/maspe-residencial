package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantBillingInvoiceLineRepository extends JpaRepository<TenantBillingInvoiceLine, Long> {
    List<TenantBillingInvoiceLine> findByTenantIdAndInvoice_IdOrderByIdAsc(Long tenantId, Long invoiceId);
}
