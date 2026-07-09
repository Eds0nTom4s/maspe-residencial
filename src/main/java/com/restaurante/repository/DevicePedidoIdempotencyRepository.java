package com.restaurante.repository;

import com.restaurante.model.entity.DevicePedidoIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DevicePedidoIdempotencyRepository extends JpaRepository<DevicePedidoIdempotencyRecord, Long> {
    Optional<DevicePedidoIdempotencyRecord> findByTenantIdAndDispositivoIdAndIdempotencyKey(Long tenantId, Long dispositivoId, String idempotencyKey);
    Optional<DevicePedidoIdempotencyRecord> findByTenantIdAndDispositivoIdAndClientRequestId(Long tenantId, Long dispositivoId, String clientRequestId);
}

