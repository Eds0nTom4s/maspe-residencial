package com.restaurante.repository;

import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Set;

public interface OperationalEventLogRepository extends JpaRepository<OperationalEventLog, Long> {

    Page<OperationalEventLog> findByTenantId(Long tenantId, Pageable pageable);

    List<OperationalEventLog> findByTenantIdAndEventType(Long tenantId, OperationalEventType eventType);

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

    long countByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime de, LocalDateTime ate);

    long countByTenantIdAndCreatedAtBefore(Long tenantId, LocalDateTime before);

    @Modifying
    @Query("delete from OperationalEventLog e where e.tenant.id = :tenantId and e.createdAt < :before")
    int deleteByTenantIdAndCreatedAtBefore(@Param("tenantId") Long tenantId, @Param("before") LocalDateTime before);

    @Query("""
            select e.eventType as key, count(e) as cnt
            from OperationalEventLog e
            where e.tenant.id = :tenantId
              and e.createdAt >= :de and e.createdAt <= :ate
            group by e.eventType
            """)
    List<Object[]> countByEventType(@Param("tenantId") Long tenantId, @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

    @Query("""
            select e.origem as key, count(e) as cnt
            from OperationalEventLog e
            where e.tenant.id = :tenantId
              and e.createdAt >= :de and e.createdAt <= :ate
            group by e.origem
            """)
    List<Object[]> countByOrigem(@Param("tenantId") Long tenantId, @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

    @Query("""
            select max(e.createdAt)
            from OperationalEventLog e
              join e.subPedido sp
            where e.tenant.id = :tenantId
              and sp.unidadeProducao.id = :unidadeProducaoId
              and (:de is null or e.createdAt >= :de)
              and (:ate is null or e.createdAt <= :ate)
            """)
    LocalDateTime maxCreatedAtByTenantAndUnidadeProducaoAndPeriod(
            @Param("tenantId") Long tenantId,
            @Param("unidadeProducaoId") Long unidadeProducaoId,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate
    );

    @Query("""
            select max(e.id)
            from OperationalEventLog e
              join e.subPedido sp
            where e.tenant.id = :tenantId
              and sp.unidadeProducao.id = :unidadeProducaoId
            """)
    Long maxIdByTenantAndUnidadeProducao(@Param("tenantId") Long tenantId, @Param("unidadeProducaoId") Long unidadeProducaoId);

    @Query("""
            select e
            from OperationalEventLog e
              join e.subPedido sp
            where e.tenant.id = :tenantId
              and sp.unidadeProducao.id = :unidadeProducaoId
              and e.id > :sinceId
              and e.eventType in :types
            order by e.id asc
            """)
    List<OperationalEventLog> findFilaEventsAfter(@Param("tenantId") Long tenantId,
                                                 @Param("unidadeProducaoId") Long unidadeProducaoId,
                                                 @Param("sinceId") Long sinceId,
                                                 @Param("types") Collection<OperationalEventType> types,
                                                 Pageable pageable);

    @Query("""
            select count(e)
            from OperationalEventLog e
              join e.subPedido sp
            where e.tenant.id = :tenantId
              and sp.unidadeProducao.id = :unidadeProducaoId
              and e.id > :sinceId
              and e.eventType in :types
            """)
    long countFilaEventsAfter(@Param("tenantId") Long tenantId,
                              @Param("unidadeProducaoId") Long unidadeProducaoId,
                              @Param("sinceId") Long sinceId,
                              @Param("types") Collection<OperationalEventType> types);

    @Query("""
            select case when count(e) > 0 then true else false end
            from OperationalEventLog e
              join e.subPedido sp
            where e.id = :eventId
              and e.tenant.id = :tenantId
              and sp.unidadeProducao.id = :unidadeProducaoId
            """)
    boolean existsByIdAndTenantAndUnidadeProducao(@Param("eventId") Long eventId,
                                                 @Param("tenantId") Long tenantId,
                                                 @Param("unidadeProducaoId") Long unidadeProducaoId);

    List<OperationalEventLog> findTop20ByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(Long tenantId, OperationalEntityType entityType, Long entityId);

    @Query("""
            select e
            from OperationalEventLog e
            where e.tenant.id = :tenantId
              and e.turno.id = :turnoId
              and e.eventType in :eventTypes
            order by e.createdAt desc
            """)
    List<OperationalEventLog> findTopByTenantAndTurnoAndEventTypes(@Param("tenantId") Long tenantId,
                                                                  @Param("turnoId") Long turnoId,
                                                                  @Param("eventTypes") Set<OperationalEventType> eventTypes,
                                                                  Pageable pageable);

    @Query("""
            select e.tenant.id as tenantId, max(e.createdAt) as lastAt
            from OperationalEventLog e
            where e.createdAt >= :from
            group by e.tenant.id
            """)
    List<Object[]> maxActivityByTenantSince(@Param("from") LocalDateTime from);

    @Query("""
            select e
            from OperationalEventLog e
            where e.tenant.id = :tenantId
              and (:eventType is null or e.eventType = :eventType)
              and (:entityType is null or e.entityType = :entityType)
              and (:actorType is null or e.actorType = :actorType)
              and (:de is null or e.createdAt >= :de)
              and (:ate is null or e.createdAt <= :ate)
            order by e.createdAt desc
            """)
    Page<OperationalEventLog> searchTenantEventsExtended(
            @Param("tenantId") Long tenantId,
            @Param("eventType") OperationalEventType eventType,
            @Param("entityType") OperationalEntityType entityType,
            @Param("actorType") com.restaurante.model.enums.OperationalActorType actorType,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate,
            Pageable pageable
    );
}
