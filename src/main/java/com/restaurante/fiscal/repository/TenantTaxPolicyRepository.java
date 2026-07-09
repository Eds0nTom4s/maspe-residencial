package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.TenantTaxPolicy;
import com.restaurante.model.enums.TenantTaxPolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TenantTaxPolicyRepository extends JpaRepository<TenantTaxPolicy, Long> {

    @Query("""
            select p from TenantTaxPolicy p
            where p.tenant.id = :tenantId
              and (:status is null or p.status = :status)
            order by p.createdAt desc
            """)
    Page<TenantTaxPolicy> listByTenant(@Param("tenantId") Long tenantId,
                                       @Param("status") TenantTaxPolicyStatus status,
                                       Pageable pageable);

    @Query("""
            select p from TenantTaxPolicy p
            where p.tenant.id = :tenantId
              and p.status = 'ACTIVE'
              and (:at is null
                   or ((p.effectiveFrom is null or p.effectiveFrom <= :at)
                       and (p.effectiveTo is null or p.effectiveTo >= :at)))
            order by p.createdAt desc
            """)
    Optional<TenantTaxPolicy> findActiveEffective(@Param("tenantId") Long tenantId, @Param("at") LocalDateTime at);
}

