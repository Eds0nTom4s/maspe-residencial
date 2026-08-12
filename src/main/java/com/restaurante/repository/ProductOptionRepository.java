package com.restaurante.repository;

import com.restaurante.model.entity.ProductOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    List<ProductOption> findByTenantIdAndOptionGroupIdInOrderBySortOrderAscPublicIdAsc(Long tenantId, Collection<Long> groupIds);
    List<ProductOption> findByTenantIdAndOptionGroupIdInAndActiveTrueOrderBySortOrderAscPublicIdAsc(Long tenantId, Collection<Long> groupIds);
    Optional<ProductOption> findByTenantIdAndPublicId(Long tenantId, UUID publicId);
    Optional<ProductOption> findByPublicId(UUID publicId);
    Optional<ProductOption> findByTenantIdAndId(Long tenantId, Long id);
}
