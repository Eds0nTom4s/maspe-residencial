package com.restaurante.delivery.repository;

import com.restaurante.model.entity.OrderFulfillment;
import com.restaurante.model.enums.OrderFulfillmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderFulfillmentRepository extends JpaRepository<OrderFulfillment, Long> {
    Optional<OrderFulfillment> findByTenantIdAndPedido_Id(Long tenantId, Long pedidoId);
    Optional<OrderFulfillment> findByTenantIdAndId(Long tenantId, Long id);
    List<OrderFulfillment> findByTenantIdAndStatusOrderByIdDesc(Long tenantId, OrderFulfillmentStatus status);
}

