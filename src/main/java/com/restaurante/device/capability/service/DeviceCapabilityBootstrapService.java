package com.restaurante.device.capability.service;

import com.restaurante.device.capability.entity.DeviceOperationalCapabilityEntity;
import com.restaurante.device.capability.repository.DeviceOperationalCapabilityRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.service.device.DeviceCapabilities;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceCapabilityBootstrapService {

    private final DeviceOperationalCapabilityRepository repository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public void ensureDefaults(DispositivoOperacional device, String ip, String userAgent) {
        if (device == null || device.getTenant() == null || device.getTenant().getId() == null || device.getId() == null) return;
        long existing = repository.countByTenantAndDevice(device.getTenant().getId(), device.getId());
        if (existing > 0) return;

        Set<DeviceCapability> caps = EnumSet.noneOf(DeviceCapability.class);
        caps.addAll(DeviceCapabilities.forTipo(device.getTipo()));
        caps.addAll(DeviceCapabilityDefaults.additionalDefaultsByOperationalType(device.getOperationalDeviceType()));

        for (DeviceCapability c : caps) {
            DeviceOperationalCapabilityEntity e = new DeviceOperationalCapabilityEntity();
            e.setTenant(device.getTenant());
            e.setDispositivoOperacional(device);
            e.setCapability(c);
            e.setEnabled(true);
            e.setSource("SYSTEM_DEFAULT");
            repository.save(e);
        }

        operationalEventLogService.logPublicEvent(
                device.getTenant(),
                device.getInstituicao(),
                device.getUnidadeAtendimento(),
                null,
                null,
                OperationalEventType.DEVICE_CAPABILITY_DEFAULTS_BOOTSTRAPPED,
                OperationalEntityType.DISPOSITIVO_OPERACIONAL,
                device.getId(),
                OperationalOrigem.SYSTEM,
                "Device capabilities defaults bootstrapped",
                Map.of("tenantId", device.getTenant().getId(), "deviceId", device.getId(), "count", caps.size()),
                ip,
                userAgent
        );
    }

    @Transactional(readOnly = true)
    public List<DeviceCapability> listEnabledCapabilities(Long tenantId, Long deviceId) {
        return repository.findByTenantAndDevice(tenantId, deviceId).stream()
                .filter(DeviceOperationalCapabilityEntity::isEnabled)
                .map(DeviceOperationalCapabilityEntity::getCapability)
                .toList();
    }
}
