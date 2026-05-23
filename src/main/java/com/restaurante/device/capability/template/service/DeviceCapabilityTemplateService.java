package com.restaurante.device.capability.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplate;
import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplateItem;
import com.restaurante.device.capability.template.repository.DeviceCapabilityTemplateItemRepository;
import com.restaurante.device.capability.template.repository.DeviceCapabilityTemplateRepository;
import com.restaurante.dto.request.CreateDeviceCapabilityTemplateRequest;
import com.restaurante.dto.request.DeviceCapabilityTemplateItemRequest;
import com.restaurante.dto.request.UpdateDeviceCapabilityTemplateRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DeviceCapabilityTemplateStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCapabilityTemplateService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final DeviceCapabilityTemplateRepository templateRepository;
    private final DeviceCapabilityTemplateItemRepository itemRepository;
    private final DeviceCapabilityTemplateBootstrapService bootstrapService;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DeviceCapabilityTemplate> list() {
        TenantContext ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", ctx.tenantId()));
        bootstrapService.ensureDefaults(tenant);
        return templateRepository.listByTenant(ctx.tenantId());
    }

    @Transactional(readOnly = true)
    public DeviceCapabilityTemplate get(Long templateId) {
        Long tenantId = tenantGuard.requireContext().tenantId();
        return templateRepository.findByIdAndTenant_Id(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceCapabilityTemplate", "id", templateId));
    }

    @Transactional(readOnly = true)
    public List<DeviceCapabilityTemplateItem> listItems(Long templateId) {
        Long tenantId = tenantGuard.requireContext().tenantId();
        return itemRepository.listByTenantAndTemplate(tenantId, templateId);
    }

    @Transactional
    public DeviceCapabilityTemplate create(CreateDeviceCapabilityTemplateRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", ctx.tenantId()));

        validateTemplateItems(req.getItems(), ctx);
        if (templateRepository.findByTenant_IdAndCode(ctx.tenantId(), req.getCode()).isPresent()) {
            throw new BusinessException("TEMPLATE_CODE_ALREADY_EXISTS");
        }

        DeviceCapabilityTemplate t = new DeviceCapabilityTemplate();
        t.setTenant(tenant);
        t.setCode(req.getCode());
        t.setName(req.getName());
        t.setDescription(req.getDescription());
        t.setTargetDeviceType(req.getTargetDeviceType());
        t.setStatus(req.getStatus());
        t.setSystemDefault(false);
        t.setVersion(1);
        t.setCreatedBy(ctx.userId());
        templateRepository.save(t);

        saveItems(tenant, t, req.getItems());

        operationalEventLogService.logPublicEvent(
                tenant, null, null, null, null,
                OperationalEventType.DEVICE_CAPABILITY_TEMPLATE_CREATED,
                OperationalEntityType.DEVICE_CAPABILITY,
                t.getId(),
                OperationalOrigem.SYSTEM,
                "Device capability template created",
                Map.of("tenantId", tenant.getId(), "templateId", t.getId(), "templateCode", t.getCode()),
                ip, userAgent
        );

        return t;
    }

    @Transactional
    public DeviceCapabilityTemplate update(Long templateId, UpdateDeviceCapabilityTemplateRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        DeviceCapabilityTemplate t = templateRepository.findByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("DeviceCapabilityTemplate", "id", templateId));

        validateTemplateItems(req.getItems(), ctx);

        t.setName(req.getName());
        t.setDescription(req.getDescription());
        t.setTargetDeviceType(req.getTargetDeviceType());
        t.setStatus(req.getStatus());
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedBy(ctx.userId());
        templateRepository.save(t);

        itemRepository.deleteByTenant_IdAndTemplate_Id(ctx.tenantId(), templateId);
        saveItems(t.getTenant(), t, req.getItems());

        operationalEventLogService.logPublicEvent(
                t.getTenant(), null, null, null, null,
                OperationalEventType.DEVICE_CAPABILITY_TEMPLATE_UPDATED,
                OperationalEntityType.DEVICE_CAPABILITY,
                t.getId(),
                OperationalOrigem.SYSTEM,
                "Device capability template updated",
                Map.of("tenantId", ctx.tenantId(), "templateId", t.getId(), "templateCode", t.getCode(), "version", t.getVersion()),
                ip, userAgent
        );

        return t;
    }

    @Transactional
    public DeviceCapabilityTemplate activate(Long templateId, boolean active, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        DeviceCapabilityTemplate t = templateRepository.findByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("DeviceCapabilityTemplate", "id", templateId));

        t.setStatus(active ? DeviceCapabilityTemplateStatus.ACTIVE : DeviceCapabilityTemplateStatus.INACTIVE);
        t.setUpdatedBy(ctx.userId());
        templateRepository.save(t);

        operationalEventLogService.logPublicEvent(
                t.getTenant(), null, null, null, null,
                active ? OperationalEventType.DEVICE_CAPABILITY_TEMPLATE_ACTIVATED : OperationalEventType.DEVICE_CAPABILITY_TEMPLATE_DEACTIVATED,
                OperationalEntityType.DEVICE_CAPABILITY,
                t.getId(),
                OperationalOrigem.SYSTEM,
                active ? "Device capability template activated" : "Device capability template deactivated",
                Map.of("tenantId", ctx.tenantId(), "templateId", t.getId(), "templateCode", t.getCode()),
                ip, userAgent
        );
        return t;
    }

    private void validateTemplateItems(List<DeviceCapabilityTemplateItemRequest> items, TenantContext ctx) {
        if (items == null) return;
        boolean hasCrossUnit = items.stream().anyMatch(i -> i != null && i.getCapability() == DeviceCapability.CROSS_UNIT_ASSISTED_IDENTIFICATION && Boolean.TRUE.equals(i.getEnabled()));
        if (hasCrossUnit) {
            // somente OWNER/ADMIN pode incluir CROSS_UNIT
            if (!tenantGuard.hasAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN)) {
                throw new BusinessException("CROSS_UNIT_CAPABILITY_REQUIRES_OWNER_ADMIN");
            }
        }
    }

    private void saveItems(Tenant tenant, DeviceCapabilityTemplate t, List<DeviceCapabilityTemplateItemRequest> items) {
        if (items == null) return;
        for (DeviceCapabilityTemplateItemRequest it : items) {
            if (it == null || it.getCapability() == null) continue;
            DeviceCapabilityTemplateItem item = new DeviceCapabilityTemplateItem();
            item.setTenant(tenant);
            item.setTemplate(t);
            item.setCapability(it.getCapability());
            item.setEnabled(Boolean.TRUE.equals(it.getEnabled()));
            item.setOverrideReason(it.getOverrideReason());
            try {
                item.setMetadataJson(it.getMetadata() != null ? objectMapper.writeValueAsString(it.getMetadata()) : null);
            } catch (Exception ignored) { }
            itemRepository.save(item);
        }
    }
}

