package com.restaurante.device.offline.repository;

import com.restaurante.device.offline.entity.DeviceOfflineCommandReplayAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceOfflineCommandReplayAttemptRepository extends JpaRepository<DeviceOfflineCommandReplayAttempt, Long> {

    @Query("""
            select a
              from DeviceOfflineCommandReplayAttempt a
             where a.tenant.id = :tenantId
               and a.serverSyncId = :serverSyncId
             order by a.requestedAt desc
            """)
    Page<DeviceOfflineCommandReplayAttempt> findByTenantAndServerSyncId(@Param("tenantId") Long tenantId,
                                                                        @Param("serverSyncId") String serverSyncId,
                                                                        Pageable pageable);

    @Query("""
            select a
              from DeviceOfflineCommandReplayAttempt a
             where a.tenant.id = :tenantId
               and a.command.id = :commandId
             order by a.requestedAt desc
            """)
    Page<DeviceOfflineCommandReplayAttempt> findByTenantAndCommand(@Param("tenantId") Long tenantId,
                                                                   @Param("commandId") Long commandId,
                                                                   Pageable pageable);

    @Query("""
            select coalesce(max(a.attemptNumber),0)
              from DeviceOfflineCommandReplayAttempt a
             where a.tenant.id = :tenantId
               and a.command.id = :commandId
            """)
    int maxAttemptNumber(@Param("tenantId") Long tenantId, @Param("commandId") Long commandId);
}

