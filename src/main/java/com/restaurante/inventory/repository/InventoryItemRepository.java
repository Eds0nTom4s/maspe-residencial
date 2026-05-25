package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryItem;
import com.restaurante.model.enums.InventoryItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Query("""
            select i from InventoryItem i
            where i.tenant.id = :tenantId
              and (:status is null or i.status = :status)
            order by i.name asc, i.id asc
            """)
    Page<InventoryItem> listByTenant(@Param("tenantId") Long tenantId,
                                     @Param("status") InventoryItemStatus status,
                                     Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") Long id);
}

