package com.restaurante.device.capability.repository;

import com.restaurante.device.capability.entity.DeviceOperationalCapabilityEntity;
import com.restaurante.model.enums.DeviceCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceOperationalCapabilityRepository extends JpaRepository<DeviceOperationalCapabilityEntity, Long> {

    @Query("""
            select c
            from DeviceOperationalCapabilityEntity c
            where c.tenant.id = :tenantId
              and c.dispositivoOperacional.id = :deviceId
            order by c.capability asc
            """)
    List<DeviceOperationalCapabilityEntity> findByTenantAndDevice(@Param("tenantId") Long tenantId, @Param("deviceId") Long deviceId);

    Optional<DeviceOperationalCapabilityEntity> findByTenant_IdAndDispositivoOperacional_IdAndCapability(Long tenantId, Long deviceId, DeviceCapability capability);

    @Query("""
            select count(c)
            from DeviceOperationalCapabilityEntity c
            where c.tenant.id = :tenantId
              and c.dispositivoOperacional.id = :deviceId
            """)
    long countByTenantAndDevice(@Param("tenantId") Long tenantId, @Param("deviceId") Long deviceId);
}

