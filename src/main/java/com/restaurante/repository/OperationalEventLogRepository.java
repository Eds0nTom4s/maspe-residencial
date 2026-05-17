package com.restaurante.repository;

import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.enums.OperationalEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OperationalEventLogRepository extends JpaRepository<OperationalEventLog, Long> {

    Page<OperationalEventLog> findByTenantId(Long tenantId, Pageable pageable);

    Page<OperationalEventLog> findByTenantIdAndPedidoId(Long tenantId, Long pedidoId, Pageable pageable);

    Page<OperationalEventLog> findByTenantIdAndSubPedidoId(Long tenantId, Long subPedidoId, Pageable pageable);

    @Query("""
            select e from OperationalEventLog e
            where e.tenant.id = :tenantId
              and (:pedidoId is null or e.pedido.id = :pedidoId)
              and (:subPedidoId is null or e.subPedido.id = :subPedidoId)
              and (:eventType is null or e.eventType = :eventType)
              and (:actorUserId is null or e.actorUser.id = :actorUserId)
              and (:deviceId is null or e.dispositivo.id = :deviceId)
              and (:de is null or e.createdAt >= :de)
              and (:ate is null or e.createdAt <= :ate)
            order by e.createdAt desc
            """)
    Page<OperationalEventLog> searchByTenantAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("pedidoId") Long pedidoId,
            @Param("subPedidoId") Long subPedidoId,
            @Param("eventType") OperationalEventType eventType,
            @Param("actorUserId") Long actorUserId,
            @Param("deviceId") Long deviceId,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate,
            Pageable pageable
    );
}

