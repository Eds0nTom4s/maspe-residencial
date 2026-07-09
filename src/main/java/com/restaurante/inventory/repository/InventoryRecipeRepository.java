package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryRecipe;
import com.restaurante.model.enums.InventoryRecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryRecipeRepository extends JpaRepository<InventoryRecipe, Long> {

    @Query("""
            select r from InventoryRecipe r
            where r.tenant.id = :tenantId
              and r.product.id = :productId
              and r.status = :status
              and (:at is null or (coalesce(r.effectiveFrom, :at) <= :at and coalesce(r.effectiveTo, :at) >= :at))
            order by r.effectiveFrom desc nulls last, r.id desc
            """)
    List<InventoryRecipe> findEffectiveByProduct(@Param("tenantId") Long tenantId,
                                                 @Param("productId") Long productId,
                                                 @Param("status") InventoryRecipeStatus status,
                                                 @Param("at") LocalDateTime at);

    Optional<InventoryRecipe> findByTenantIdAndId(Long tenantId, Long id);
}

