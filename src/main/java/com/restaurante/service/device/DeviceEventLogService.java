package com.restaurante.service.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.DeviceEventLog;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceEventStatus;
import com.restaurante.model.enums.DeviceEventType;
import com.restaurante.repository.DeviceEventLogRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceEventLogService {

    private final DeviceEventLogRepository repository;
    private final TenantRepository tenantRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void log(Long tenantId,
                    Long dispositivoId,
                    DeviceEventType type,
                    DeviceEventStatus status,
                    String message,
                    Map<String, Object> metadata,
                    String ip,
                    String userAgent) {
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.getReferenceById(dispositivoId);

        DeviceEventLog e = new DeviceEventLog();
        e.setTenant(tenant);
        e.setDispositivo(dispositivo);
        e.setEventType(type);
        e.setStatus(status);
        e.setMessage(message);
        e.setMetadataJson(metadata != null ? toJson(metadata) : null);
        e.setIp(ip);
        e.setUserAgent(userAgent);
        repository.save(e);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"metadata_json_serialization_failed\"}";
        }
    }
}

