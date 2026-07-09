package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingPaymentSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantBillingPaymentSequenceRepository extends JpaRepository<TenantBillingPaymentSequence, Long> {
    Optional<TenantBillingPaymentSequence> findByTenantIdAndSeqYear(Long tenantId, Integer seqYear);
}

