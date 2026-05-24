package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.ProductTaxClassification;
import com.restaurante.model.enums.ProductTaxClassificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductTaxClassificationRepository extends JpaRepository<ProductTaxClassification, Long> {

    @Query("""
            select c from ProductTaxClassification c
            where c.tenant.id = :tenantId
              and (:status is null or c.status = :status)
            order by c.createdAt desc
            """)
    Page<ProductTaxClassification> listByTenant(@Param("tenantId") Long tenantId,
                                                @Param("status") ProductTaxClassificationStatus status,
                                                Pageable pageable);

    @Query("""
            select c from ProductTaxClassification c
            where c.tenant.id = :tenantId
              and c.product.id = :productId
              and c.status = 'ACTIVE'
              and (:at is null
                   or ((c.effectiveFrom is null or c.effectiveFrom <= :at)
                       and (c.effectiveTo is null or c.effectiveTo >= :at)))
            """)
    Optional<ProductTaxClassification> findActiveEffectiveByTenantAndProduct(@Param("tenantId") Long tenantId,
                                                                             @Param("productId") Long productId,
                                                                             @Param("at") LocalDateTime at);
}

