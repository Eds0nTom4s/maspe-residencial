package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryConsumptionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryConsumptionRecordRepository extends JpaRepository<InventoryConsumptionRecord, Long> {
    Optional<InventoryConsumptionRecord> findByTenantIdAndPedidoId(Long tenantId, Long pedidoId);

    @Query("""
            select r from InventoryConsumptionRecord r
            where r.tenant.id = :tenantId
            order by r.createdAt desc, r.id desc
            """)
    Page<InventoryConsumptionRecord> listByTenant(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("""
            select r from InventoryConsumptionRecord r
            where r.tenant.id = :tenantId
              and (cast(:from as timestamp) is null or r.createdAt >= :from)
              and (cast(:to as timestamp) is null or r.createdAt <= :to)
            order by r.createdAt asc, r.id asc
            """)
    List<InventoryConsumptionRecord> findAllForEvidence(@Param("tenantId") Long tenantId,
                                                        @Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to);
}
