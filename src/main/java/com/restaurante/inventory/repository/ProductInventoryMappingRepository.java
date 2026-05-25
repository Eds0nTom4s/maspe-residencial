package com.restaurante.inventory.repository;

import com.restaurante.model.entity.ProductInventoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductInventoryMappingRepository extends JpaRepository<ProductInventoryMapping, Long> {
    Optional<ProductInventoryMapping> findByTenantIdAndProductId(Long tenantId, Long productId);

    List<ProductInventoryMapping> findAllByTenantIdOrderByIdAsc(Long tenantId);
}

