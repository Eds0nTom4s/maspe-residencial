package com.restaurante.device.capability.service;

import com.restaurante.device.capability.entity.DeviceOperationalCapabilityEntity;
import com.restaurante.device.capability.repository.DeviceOperationalCapabilityRepository;
import com.restaurante.dto.request.UpdateDeviceCapabilityRequest;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.DispositivoOperacionalRepository;
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
public class TenantDeviceCapabilityService {

    private final TenantGuard tenantGuard;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final DeviceOperationalCapabilityRepository capabilityRepository;
    private final DeviceCapabilityBootstrapService bootstrapService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public List<DeviceOperationalCapabilityEntity> list(Long deviceId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        Long tenantId = TenantContextHolder.get().orElseThrow().tenantId();
        DispositivoOperacional device = dispositivoOperacionalRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DispositivoOperacional", "id", deviceId));
        // lazy bootstrap para garantir baseline
        bootstrapService.ensureDefaults(device, null, null);
        return capabilityRepository.findByTenantAndDevice(tenantId, deviceId);
    }

    @Transactional
    public DeviceOperationalCapabilityEntity upsert(Long deviceId, DeviceCapability capability, UpdateDeviceCapabilityRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        Long tenantId = TenantContextHolder.get().orElseThrow().tenantId();
        DispositivoOperacional device = dispositivoOperacionalRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DispositivoOperacional", "id", deviceId));
        bootstrapService.ensureDefaults(device, ip, userAgent);

        DeviceOperationalCapabilityEntity e = capabilityRepository.findByTenant_IdAndDispositivoOperacional_IdAndCapability(tenantId, deviceId, capability)
                .orElseGet(() -> {
                    DeviceOperationalCapabilityEntity n = new DeviceOperationalCapabilityEntity();
                    n.setTenant(device.getTenant());
                    n.setDispositivoOperacional(device);
                    n.setCapability(capability);
                    n.setSource("MANUAL");
                    n.setEnabled(true);
                    return n;
                });
        e.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
        e.setSource("MANUAL");
        // edição manual protege contra futuros rollouts
        e.setManualOverride(true);
        e.setTemplateManaged(false);
        e.setSourceTemplateId(null);
        e.setSourceRolloutId(null);
        e.setTemplateAppliedAt(null);
        DeviceOperationalCapabilityEntity saved = capabilityRepository.save(e);

        operationalEventLogService.logGeneric(
                OperationalEventType.DEVICE_CAPABILITY_UPDATED,
                OperationalEntityType.DEVICE_CAPABILITY,
                saved.getId(),
                OperationalOrigem.SYSTEM,
                "Device capability updated",
                Map.of(
                        "tenantId", tenantId,
                        "deviceId", deviceId,
                        "unidadeId", device.getUnidadeAtendimento() != null ? device.getUnidadeAtendimento().getId() : null,
                        "capability", capability.name(),
                        "enabled", saved.isEnabled()
                ),
                ip,
                userAgent
        );

        return saved;
    }
}
