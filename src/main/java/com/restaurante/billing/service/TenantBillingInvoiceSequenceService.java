package com.restaurante.billing.service;

import com.restaurante.billing.config.BillingProperties;
import com.restaurante.billing.repository.TenantBillingInvoiceSequenceRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantBillingInvoiceSequence;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TenantBillingInvoiceSequenceService {

    private final BillingProperties props;
    private final TenantBillingInvoiceSequenceRepository repository;
    private final TenantRepository tenantRepository;

    @Transactional
    public String nextNumber(Long tenantId, LocalDateTime at) {
        if (tenantId == null) throw new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND");
        int year = (at != null ? at : LocalDateTime.now()).getYear();
        TenantBillingInvoiceSequence seq = repository.findKeyForUpdate(tenantId, year)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_BILLING_INVOICE_NOT_FOUND"));
                    TenantBillingInvoiceSequence s = new TenantBillingInvoiceSequence();
                    s.setTenant(tenant);
                    s.setYear(year);
                    s.setCurrentNumber(0L);
                    s.setStatus("ACTIVE");
                    return repository.save(s);
                });

        if (!"ACTIVE".equalsIgnoreCase(seq.getStatus())) {
            throw new BusinessException("TENANT_BILLING_INVOICE_INVALID_STATE");
        }

        long next = (seq.getCurrentNumber() != null ? seq.getCurrentNumber() : 0L) + 1L;
        seq.setCurrentNumber(next);
        repository.save(seq);

        return format(props.getInvoice().getSequencePrefix(), year, next);
    }

    private static String format(String prefix, int year, long n) {
        String p = (prefix == null || prefix.isBlank()) ? "CONS-BILL" : prefix.trim();
        return p + "-" + year + "-" + String.format("%06d", n);
    }
}

