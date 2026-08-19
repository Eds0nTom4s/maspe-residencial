package com.restaurante.repository;

import com.restaurante.model.entity.TenantPdvOrderIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantPdvOrderIdempotencyRepository extends JpaRepository<TenantPdvOrderIdempotencyRecord, Long> {
    Optional<TenantPdvOrderIdempotencyRecord> findByTenantIdAndUserIdAndIdempotencyKey(
            Long tenantId, Long userId, String idempotencyKey);

    Optional<TenantPdvOrderIdempotencyRecord> findByTenantIdAndUserIdAndClientRequestId(
            Long tenantId, Long userId, String clientRequestId);
}
