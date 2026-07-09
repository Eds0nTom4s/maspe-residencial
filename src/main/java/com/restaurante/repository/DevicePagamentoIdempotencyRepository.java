package com.restaurante.repository;

import com.restaurante.model.entity.DevicePagamentoIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DevicePagamentoIdempotencyRepository extends JpaRepository<DevicePagamentoIdempotencyRecord, Long> {
    Optional<DevicePagamentoIdempotencyRecord> findByTenantIdAndDispositivoIdAndIdempotencyKey(Long tenantId, Long dispositivoId, String idempotencyKey);
    Optional<DevicePagamentoIdempotencyRecord> findByTenantIdAndDispositivoIdAndClientRequestId(Long tenantId, Long dispositivoId, String clientRequestId);
}

