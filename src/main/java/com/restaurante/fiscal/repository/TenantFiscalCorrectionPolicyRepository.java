package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.TenantFiscalCorrectionPolicy;
import com.restaurante.model.enums.TenantFiscalCorrectionPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TenantFiscalCorrectionPolicyRepository extends JpaRepository<TenantFiscalCorrectionPolicy, Long> {

    Optional<TenantFiscalCorrectionPolicy> findByTenantId(Long tenantId);

    @Query("""
            select p from TenantFiscalCorrectionPolicy p
            where p.tenant.id = :tenantId
              and p.status = :status
              and (:at is null or ((p.effectiveFrom is null or p.effectiveFrom <= :at) and (p.effectiveTo is null or p.effectiveTo >= :at)))
            """)
    Optional<TenantFiscalCorrectionPolicy> findActiveEffective(@Param("tenantId") Long tenantId,
                                                               @Param("status") TenantFiscalCorrectionPolicyStatus status,
                                                               @Param("at") LocalDateTime at);
}

