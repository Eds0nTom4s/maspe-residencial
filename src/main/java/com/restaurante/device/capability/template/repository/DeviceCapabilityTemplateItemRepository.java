package com.restaurante.device.capability.template.repository;

import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceCapabilityTemplateItemRepository extends JpaRepository<DeviceCapabilityTemplateItem, Long> {

    @Query("""
            select i
            from DeviceCapabilityTemplateItem i
            where i.tenant.id = :tenantId
              and i.template.id = :templateId
            order by i.id asc
            """)
    List<DeviceCapabilityTemplateItem> listByTenantAndTemplate(@Param("tenantId") Long tenantId, @Param("templateId") Long templateId);

    void deleteByTenant_IdAndTemplate_Id(Long tenantId, Long templateId);
}

