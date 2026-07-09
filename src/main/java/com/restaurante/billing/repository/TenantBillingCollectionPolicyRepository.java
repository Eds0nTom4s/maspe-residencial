package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantBillingCollectionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantBillingCollectionPolicyRepository extends JpaRepository<TenantBillingCollectionPolicy, Long> {
    Optional<TenantBillingCollectionPolicy> findByTenantId(Long tenantId);
}

