package com.restaurante.device.offline.repository;

import com.restaurante.device.offline.entity.DeviceOfflineReplayOperationItem;
import com.restaurante.model.enums.DeviceOfflineReplayOperationItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DeviceOfflineReplayOperationItemRepository extends JpaRepository<DeviceOfflineReplayOperationItem, Long> {

    @Query("""
            select i
            from DeviceOfflineReplayOperationItem i
            where i.tenant.id = :tenantId
              and i.operation.operationId = :operationId
              and (:allStatuses = true or i.itemStatus in :statuses)
            order by i.id asc
            """)
    Page<DeviceOfflineReplayOperationItem> listByOperation(
            @Param("tenantId") Long tenantId,
            @Param("operationId") String operationId,
            @Param("statuses") Collection<DeviceOfflineReplayOperationItemStatus> statuses,
            @Param("allStatuses") boolean allStatuses,
            Pageable pageable
    );

    @Query("""
            select count(i)
            from DeviceOfflineReplayOperationItem i
            where i.tenant.id = :tenantId
              and i.operation.id = :operationDbId
              and i.itemStatus in :statuses
            """)
    long countByOperationAndStatuses(@Param("tenantId") Long tenantId,
                                    @Param("operationDbId") Long operationDbId,
                                    @Param("statuses") Collection<DeviceOfflineReplayOperationItemStatus> statuses);

    @Modifying
    @Query("""
            update DeviceOfflineReplayOperationItem i
               set i.itemStatus = :status,
                   i.lockedAt = :now,
                   i.lockedBy = :lockedBy
             where i.tenant.id = :tenantId
               and i.id in :ids
            """)
    int bulkMarkRunning(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("status") DeviceOfflineReplayOperationItemStatus status,
                        @Param("now") Instant now,
                        @Param("lockedBy") String lockedBy);
}

