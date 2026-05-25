package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query("""
            select m from InventoryMovement m
            where m.tenant.id = :tenantId
              and (:itemId is null or m.inventoryItem.id = :itemId)
            order by m.createdAt desc, m.id desc
            """)
    Page<InventoryMovement> listByTenant(@Param("tenantId") Long tenantId,
                                         @Param("itemId") Long itemId,
                                         Pageable pageable);

    boolean existsByTenantIdAndReferenceTypeAndReferenceIdAndMovementType(Long tenantId,
                                                                          com.restaurante.model.enums.InventoryMovementReferenceType referenceType,
                                                                          Long referenceId,
                                                                          com.restaurante.model.enums.InventoryMovementType movementType);

    @Query("""
            select m from InventoryMovement m
            where m.tenant.id = :tenantId
              and (:from is null or m.createdAt >= :from)
              and (:to is null or m.createdAt <= :to)
            order by m.createdAt asc, m.id asc
            """)
    List<InventoryMovement> findAllForEvidence(@Param("tenantId") Long tenantId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);
}
