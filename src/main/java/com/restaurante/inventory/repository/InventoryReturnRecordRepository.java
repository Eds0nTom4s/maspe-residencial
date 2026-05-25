package com.restaurante.inventory.repository;

import com.restaurante.model.entity.InventoryReturnRecord;
import com.restaurante.model.enums.InventoryReturnStatus;
import com.restaurante.model.enums.InventoryReturnType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryReturnRecordRepository extends JpaRepository<InventoryReturnRecord, Long> {

    Optional<InventoryReturnRecord> findByTenantIdAndId(Long tenantId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReturnRecord r where r.id = :id")
    Optional<InventoryReturnRecord> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select r from InventoryReturnRecord r
            where r.tenant.id = :tenantId
              and (:status is null or r.status = :status)
              and (:returnType is null or r.returnType = :returnType)
              and (:pedidoId is null or r.pedido.id = :pedidoId)
            order by r.createdAt desc, r.id desc
            """)
    Page<InventoryReturnRecord> list(@Param("tenantId") Long tenantId,
                                    @Param("status") InventoryReturnStatus status,
                                    @Param("returnType") InventoryReturnType returnType,
                                    @Param("pedidoId") Long pedidoId,
                                    Pageable pageable);

    @Query("""
            select r from InventoryReturnRecord r
            where r.tenant.id = :tenantId
              and (:from is null or r.createdAt >= :from)
              and (:to is null or r.createdAt <= :to)
            order by r.createdAt asc, r.id asc
            """)
    List<InventoryReturnRecord> findAllForEvidence(@Param("tenantId") Long tenantId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);
}
