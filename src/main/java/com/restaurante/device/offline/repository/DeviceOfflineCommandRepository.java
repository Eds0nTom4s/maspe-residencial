package com.restaurante.device.offline.repository;

import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceOfflineCommandRepository extends JpaRepository<DeviceOfflineCommand, Long> {
    Optional<DeviceOfflineCommand> findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(Long tenantId, Long dispositivoOperacionalId, String clientRequestId);
}

