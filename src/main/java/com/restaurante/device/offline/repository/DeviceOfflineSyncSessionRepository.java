package com.restaurante.device.offline.repository;

import com.restaurante.device.offline.entity.DeviceOfflineSyncSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceOfflineSyncSessionRepository extends JpaRepository<DeviceOfflineSyncSession, Long>, JpaSpecificationExecutor<DeviceOfflineSyncSession> {
    Optional<DeviceOfflineSyncSession> findByTenantIdAndDispositivoOperacionalIdAndSyncSessionId(Long tenantId, Long dispositivoOperacionalId, String syncSessionId);
    Optional<DeviceOfflineSyncSession> findByTenantIdAndServerSyncId(Long tenantId, String serverSyncId);

    Page<DeviceOfflineSyncSession> findByTenantIdAndDispositivoOperacionalIdOrderByReceivedAtDesc(Long tenantId, Long dispositivoOperacionalId, Pageable pageable);

    @Query("""
            select coalesce(s.appVersion,'(null)') as v, count(s)
              from DeviceOfflineSyncSession s
             where s.tenant.id = :tenantId
               and s.receivedAt >= :from and s.receivedAt <= :to
               and (:unidadeId is null or s.unidadeAtendimento.id = :unidadeId)
               and (:deviceId is null or s.dispositivoOperacional.id = :deviceId)
             group by s.appVersion
             order by count(s) desc
            """)
    List<Object[]> countByAppVersion(@Param("tenantId") Long tenantId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     @Param("unidadeId") Long unidadeId,
                                     @Param("deviceId") Long deviceId);
}
