package com.restaurante.device.offline.repository;

import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceOfflineCommandRepository extends JpaRepository<DeviceOfflineCommand, Long> {
    Optional<DeviceOfflineCommand> findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(Long tenantId, Long dispositivoOperacionalId, String clientRequestId);
    Page<DeviceOfflineCommand> findByTenantIdAndServerSyncIdOrderByCommandIndexAsc(Long tenantId, String serverSyncId, Pageable pageable);

    @Query("""
            select c
              from DeviceOfflineCommand c
             where c.tenant.id = :tenantId
               and c.serverSyncId = :serverSyncId
               and (:status is null or c.status = :status)
               and (:commandType is null or c.commandType = :commandType)
               and (:conflictCode is null or c.conflictCode = :conflictCode)
               and (:errorCode is null or c.errorCode = :errorCode)
             order by c.commandIndex asc
            """)
    Page<DeviceOfflineCommand> searchBySession(@Param("tenantId") Long tenantId,
                                              @Param("serverSyncId") String serverSyncId,
                                              @Param("status") com.restaurante.model.enums.DeviceOfflineCommandStatus status,
                                              @Param("commandType") com.restaurante.model.enums.DeviceOfflineCommandType commandType,
                                              @Param("conflictCode") String conflictCode,
                                              @Param("errorCode") String errorCode,
                                              Pageable pageable);

    @Query("""
            select c
              from DeviceOfflineCommand c
             where c.tenant.id = :tenantId
               and c.serverSyncId = :serverSyncId
               and (:statusesEmpty = true or c.status in :statuses)
               and (:typesEmpty = true or c.commandType in :types)
             order by c.commandIndex asc
            """)
    List<DeviceOfflineCommand> listForReplay(@Param("tenantId") Long tenantId,
                                            @Param("serverSyncId") String serverSyncId,
                                            @Param("statuses") List<com.restaurante.model.enums.DeviceOfflineCommandStatus> statuses,
                                            @Param("statusesEmpty") boolean statusesEmpty,
                                            @Param("types") List<com.restaurante.model.enums.DeviceOfflineCommandType> types,
                                            @Param("typesEmpty") boolean typesEmpty);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeviceOfflineCommand c where c.id = :id")
    Optional<DeviceOfflineCommand> findForUpdateById(@Param("id") Long id);

    @Query("""
            select c.conflictCode, count(c)
              from DeviceOfflineCommand c
             where c.tenant.id = :tenantId
               and c.receivedAt >= :from and c.receivedAt <= :to
               and c.conflictCode is not null
               and (:unidadeId is null or c.unidadeAtendimento.id = :unidadeId)
               and (:deviceId is null or c.dispositivoOperacional.id = :deviceId)
             group by c.conflictCode
             order by count(c) desc
            """)
    List<Object[]> topConflictCodes(@Param("tenantId") Long tenantId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    @Param("unidadeId") Long unidadeId,
                                    @Param("deviceId") Long deviceId,
                                    Pageable pageable);

    @Query("""
            select c.errorCode, count(c)
              from DeviceOfflineCommand c
             where c.tenant.id = :tenantId
               and c.receivedAt >= :from and c.receivedAt <= :to
               and c.errorCode is not null
               and (:unidadeId is null or c.unidadeAtendimento.id = :unidadeId)
               and (:deviceId is null or c.dispositivoOperacional.id = :deviceId)
             group by c.errorCode
             order by count(c) desc
            """)
    List<Object[]> topErrorCodes(@Param("tenantId") Long tenantId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("unidadeId") Long unidadeId,
                                 @Param("deviceId") Long deviceId,
                                 Pageable pageable);

    @Query("""
            select c.dispositivoOperacional.id, c.dispositivoOperacional.nome, count(c)
              from DeviceOfflineCommand c
             where c.tenant.id = :tenantId
               and c.receivedAt >= :from and c.receivedAt <= :to
               and c.status = com.restaurante.model.enums.DeviceOfflineCommandStatus.FAILED
               and (:unidadeId is null or c.unidadeAtendimento.id = :unidadeId)
               and (:deviceId is null or c.dispositivoOperacional.id = :deviceId)
             group by c.dispositivoOperacional.id, c.dispositivoOperacional.nome
             order by count(c) desc
            """)
    List<Object[]> deviceFailureRanking(@Param("tenantId") Long tenantId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        @Param("unidadeId") Long unidadeId,
                                        @Param("deviceId") Long deviceId,
                                        Pageable pageable);
}
