package com.restaurante.delivery.repository;

import com.restaurante.model.entity.DeliveryPricingPolicy;
import com.restaurante.model.enums.DeliveryPricingPolicyScope;
import com.restaurante.model.enums.DeliveryPricingPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryPricingPolicyRepository extends JpaRepository<DeliveryPricingPolicy, Long> {

    List<DeliveryPricingPolicy> findByTenantId(Long tenantId);

    @Query("SELECT p FROM DeliveryPricingPolicy p WHERE p.status = 'ACTIVE' " +
            "AND (p.tenant.id = :tenantId OR (p.tenant IS NULL AND p.scope = 'GLOBAL')) " +
            "AND p.effectiveFrom <= :now AND (p.effectiveTo IS NULL OR p.effectiveTo >= :now) " +
            "ORDER BY p.scope DESC, p.createdAt DESC")
    List<DeliveryPricingPolicy> findActivePolicies(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);
}
