package com.restaurante.device.capability.service;

import com.restaurante.exception.DeviceForbiddenException;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCapabilityService {

    private final OperationalEventLogService operationalEventLogService;

    @Transactional(readOnly = true)
    public boolean has(DevicePrincipal device, DeviceCapability capability) {
        if (device == null || capability == null) return false;
        List<DeviceCapability> caps = device.capabilities();
        return caps != null && caps.contains(capability);
    }

    @Transactional(readOnly = true)
    public void require(DevicePrincipal device, DeviceCapability capability, String reason, String ip, String userAgent) {
        if (has(device, capability)) return;
        auditDenied(device, capability, reason, ip, userAgent);
        throw new DeviceForbiddenException("DEVICE_CAPABILITY_REQUIRED");
    }

    @Transactional(readOnly = true)
    public void requireAll(DevicePrincipal device, List<DeviceCapability> capabilities, String reason, String ip, String userAgent) {
        if (capabilities == null || capabilities.isEmpty()) return;
        for (DeviceCapability c : capabilities) {
            if (!has(device, c)) {
                auditDenied(device, c, reason, ip, userAgent);
                throw new DeviceForbiddenException("DEVICE_CAPABILITY_REQUIRED");
            }
        }
    }

    @Transactional(readOnly = true)
    public void requireCrossUnitIfNeeded(DevicePrincipal device, Long sessaoUnidadeId, String ip, String userAgent) {
        if (device == null) throw new DeviceForbiddenException("DEVICE_CAPABILITY_REQUIRED");
        Long deviceUnidade = device.unidadeAtendimentoId();
        if (deviceUnidade == null || sessaoUnidadeId == null) return;
        if (deviceUnidade.equals(sessaoUnidadeId)) return;
        if (has(device, DeviceCapability.CROSS_UNIT_ASSISTED_IDENTIFICATION)) return;

        operationalEventLogService.logGenericRequiresNew(
                OperationalEventType.ASSISTED_IDENTIFICATION_CROSS_UNIT_DENIED,
                OperationalEntityType.DISPOSITIVO_OPERACIONAL,
                device.dispositivoId(),
                OperationalOrigem.DEVICE_POS,
                "Cross-unit assisted identification denied",
                Map.of(
                        "tenantId", device.tenantId(),
                        "deviceId", device.dispositivoId(),
                        "deviceUnidadeId", deviceUnidade,
                        "sessaoUnidadeId", sessaoUnidadeId
                ),
                ip,
                userAgent
        );
        throw new DeviceForbiddenException("DEVICE_NOT_AUTHORIZED_FOR_CROSS_UNIT_IDENTIFICATION");
    }

    private void auditDenied(DevicePrincipal device, DeviceCapability capability, String reason, String ip, String userAgent) {
        Long tenantId = device != null ? device.tenantId() : null;
        Long deviceId = device != null ? device.dispositivoId() : null;
        operationalEventLogService.logGenericRequiresNew(
                OperationalEventType.ASSISTED_IDENTIFICATION_PERMISSION_DENIED,
                OperationalEntityType.DISPOSITIVO_OPERACIONAL,
                deviceId != null ? deviceId : 0L,
                OperationalOrigem.DEVICE_POS,
                "Capability denied",
                Map.of(
                        "tenantId", tenantId,
                        "deviceId", deviceId,
                        "unidadeId", device != null ? device.unidadeAtendimentoId() : null,
                        "capability", capability != null ? capability.name() : null,
                        "reason", reason
                ),
                ip,
                userAgent
        );
    }
}
