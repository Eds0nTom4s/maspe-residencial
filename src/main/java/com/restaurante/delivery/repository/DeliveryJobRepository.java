package com.restaurante.delivery.repository;

import com.restaurante.model.entity.DeliveryJob;
import com.restaurante.model.enums.DeliveryJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, Long> {
    Optional<DeliveryJob> findByTenantIdAndPedido_Id(Long tenantId, Long pedidoId);
    Optional<DeliveryJob> findByTenantIdAndId(Long tenantId, Long id);
    List<DeliveryJob> findByTenantIdAndStatusOrderByRequestedAtDesc(Long tenantId, DeliveryJobStatus status);
}

