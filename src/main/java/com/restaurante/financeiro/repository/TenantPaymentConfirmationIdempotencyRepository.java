package com.restaurante.financeiro.repository;

import com.restaurante.model.entity.TenantPaymentConfirmationIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantPaymentConfirmationIdempotencyRepository
        extends JpaRepository<TenantPaymentConfirmationIdempotencyRecord, Long> {
    Optional<TenantPaymentConfirmationIdempotencyRecord> findByTenantIdAndUserIdAndIdempotencyKey(
            Long tenantId, Long userId, String idempotencyKey);

    Optional<TenantPaymentConfirmationIdempotencyRecord> findByTenantIdAndUserIdAndClientRequestId(
            Long tenantId, Long userId, String clientRequestId);
}
