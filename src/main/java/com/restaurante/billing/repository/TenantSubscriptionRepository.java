package com.restaurante.billing.repository;

import com.restaurante.model.entity.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {
    Optional<TenantSubscription> findTopByTenantIdOrderByIdDesc(Long tenantId);
    Optional<TenantSubscription> findByTenantIdAndId(Long tenantId, Long id);
}

