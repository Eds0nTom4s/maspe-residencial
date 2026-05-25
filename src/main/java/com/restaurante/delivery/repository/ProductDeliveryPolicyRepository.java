package com.restaurante.delivery.repository;

import com.restaurante.model.entity.ProductDeliveryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductDeliveryPolicyRepository extends JpaRepository<ProductDeliveryPolicy, Long> {
    Optional<ProductDeliveryPolicy> findByTenantIdAndProduct_Id(Long tenantId, Long productId);
    List<ProductDeliveryPolicy> findByTenantIdOrderByIdDesc(Long tenantId);
}

