package com.restaurante.repository;

import com.restaurante.model.entity.ProductOptionGroup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroup, Long> {
    List<ProductOptionGroup> findByTenantIdAndProdutoIdAndActiveTrueOrderBySortOrderAscPublicIdAsc(Long tenantId, Long produtoId);
    List<ProductOptionGroup> findByTenantIdAndProdutoIdOrderBySortOrderAscPublicIdAsc(Long tenantId, Long produtoId);
    Optional<ProductOptionGroup> findByTenantIdAndId(Long tenantId, Long id);
    Optional<ProductOptionGroup> findByTenantIdAndPublicId(Long tenantId, UUID publicId);
    boolean existsByTenantIdAndProdutoId(Long tenantId, Long produtoId);
}
