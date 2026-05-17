package com.restaurante.service.tenantadmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantAuditLog;
import com.restaurante.model.enums.TenantAuditAction;
import com.restaurante.model.enums.TenantAuditStatus;
import com.restaurante.repository.TenantAuditLogRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantAuditService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantAuditLogRepository tenantAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void log(TenantAuditAction action,
                    TenantAuditStatus status,
                    Long targetUserId,
                    String entityType,
                    Long entityId,
                    String message,
                    Map<String, Object> metadata,
                    String ip,
                    String userAgent) {

        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null || ctx.userId() == null) {
            return;
        }

        Tenant tenant = tenantRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        TenantAuditLog log = new TenantAuditLog();
        log.setTenant(tenant);
        log.setActorUserId(ctx.userId());
        log.setTargetUserId(targetUserId);
        log.setEntityType(entityType != null ? entityType : "UNKNOWN");
        log.setEntityId(entityId);
        log.setAction(action);
        log.setStatus(status);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setMessage(message);
        log.setMetadataJson(metadata != null ? toJson(metadata) : null);

        tenantAuditLogRepository.save(log);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"metadata_json_serialization_failed\"}";
        }
    }
}

