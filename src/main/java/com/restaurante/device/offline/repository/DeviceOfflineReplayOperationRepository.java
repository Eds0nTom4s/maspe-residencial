package com.restaurante.device.offline.repository;

import com.restaurante.device.offline.entity.DeviceOfflineReplayOperation;
import com.restaurante.model.enums.DeviceOfflineReplayOperationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface DeviceOfflineReplayOperationRepository extends JpaRepository<DeviceOfflineReplayOperation, Long> {

    Optional<DeviceOfflineReplayOperation> findByTenant_IdAndOperationId(Long tenantId, String operationId);

    boolean existsByTenant_IdAndServerSyncIdAndStatusIn(Long tenantId, String serverSyncId, Collection<DeviceOfflineReplayOperationStatus> statuses);

    @Query("""
            select count(o)
            from DeviceOfflineReplayOperation o
            where o.tenant.id = :tenantId
              and o.status in :statuses
            """)
    long countByTenantAndStatuses(@Param("tenantId") Long tenantId, @Param("statuses") Collection<DeviceOfflineReplayOperationStatus> statuses);

    @Query("""
            select count(o)
            from DeviceOfflineReplayOperation o
            where o.tenant.id = :tenantId
              and o.requestedAt >= :since
            """)
    long countRequestedSince(@Param("tenantId") Long tenantId, @Param("since") Instant since);

    @Modifying
    @Query("""
            update DeviceOfflineReplayOperation o
               set o.lockedAt = :now,
                   o.lockedBy = :lockedBy
             where o.id = :operationDbId
               and o.tenant.id = :tenantId
               and (o.lockedAt is null or o.lockedAt < :lockExpiredAt)
            """)
    int tryLock(@Param("tenantId") Long tenantId,
                @Param("operationDbId") Long operationDbId,
                @Param("now") Instant now,
                @Param("lockExpiredAt") Instant lockExpiredAt,
                @Param("lockedBy") String lockedBy);

    @Query("""
            select o
            from DeviceOfflineReplayOperation o
            where o.status in :statuses
              and (o.nextRetryAt is null or o.nextRetryAt <= :now)
            order by o.id asc
            """)
    Optional<DeviceOfflineReplayOperation> findNextEligible(
            @Param("statuses") Collection<DeviceOfflineReplayOperationStatus> statuses,
            @Param("now") Instant now
    );
}

