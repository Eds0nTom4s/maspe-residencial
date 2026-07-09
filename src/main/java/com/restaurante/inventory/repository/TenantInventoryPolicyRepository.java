package com.restaurante.inventory.repository;

import com.restaurante.model.entity.TenantInventoryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantInventoryPolicyRepository extends JpaRepository<TenantInventoryPolicy, Long> {
    Optional<TenantInventoryPolicy> findByTenantId(Long tenantId);
}

