package com.restaurante.delivery.repository;

import com.restaurante.model.entity.TenantDeliveryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantDeliveryPolicyRepository extends JpaRepository<TenantDeliveryPolicy, Long> {
    Optional<TenantDeliveryPolicy> findByTenantId(Long tenantId);
}

