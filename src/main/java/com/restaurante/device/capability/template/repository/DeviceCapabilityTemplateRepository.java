package com.restaurante.device.capability.template.repository;

import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceCapabilityTemplateRepository extends JpaRepository<DeviceCapabilityTemplate, Long> {

    Optional<DeviceCapabilityTemplate> findByIdAndTenant_Id(Long id, Long tenantId);

    Optional<DeviceCapabilityTemplate> findByTenant_IdAndCode(Long tenantId, String code);

    @Query("""
            select t
            from DeviceCapabilityTemplate t
            where t.tenant.id = :tenantId
            order by t.id asc
            """)
    List<DeviceCapabilityTemplate> listByTenant(@Param("tenantId") Long tenantId);
}

