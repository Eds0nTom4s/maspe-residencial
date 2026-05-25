package com.restaurante.billing.service;

import com.restaurante.billing.repository.TenantBillingPaymentSequenceRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantBillingPaymentSequence;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TenantBillingPaymentSequenceService {

    private final TenantRepository tenantRepository;
    private final TenantBillingPaymentSequenceRepository repository;

    @Transactional
    public String nextNumber(Long tenantId, LocalDateTime at) {
        if (tenantId == null) throw new BusinessException("TENANT_BILLING_PAYMENT_FORBIDDEN");
        LocalDateTime when = at != null ? at : LocalDateTime.now();
        int year = when.getYear();

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));
        TenantBillingPaymentSequence seq = repository.findByTenantIdAndSeqYear(tenantId, year).orElse(null);
        if (seq == null) {
            seq = new TenantBillingPaymentSequence();
            seq.setTenant(tenant);
            seq.setSeqYear(year);
            seq.setLastNumber(0L);
        }
        seq.setLastNumber(seq.getLastNumber() + 1);
        repository.save(seq);

        return String.format("CONS-BPAY-%d-%06d", year, seq.getLastNumber());
    }
}

