package com.restaurante.repository;

import com.restaurante.model.entity.DeviceEventLog;
import com.restaurante.model.enums.DeviceEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceEventLogRepository extends JpaRepository<DeviceEventLog, Long> {
    Page<DeviceEventLog> findByTenantId(Long tenantId, Pageable pageable);
    Page<DeviceEventLog> findByTenantIdAndEventType(Long tenantId, DeviceEventType eventType, Pageable pageable);
    Page<DeviceEventLog> findByDispositivoId(Long dispositivoId, Pageable pageable);
}

