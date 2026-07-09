package com.restaurante.dto.response;

import com.restaurante.model.enums.TenantAuditAction;
import com.restaurante.model.enums.TenantAuditStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantAuditLogResponse {
    private Long id;
    private TenantAuditAction action;
    private TenantAuditStatus status;
    private Long actorUserId;
    private Long targetUserId;
    private String message;
    private String metadataJson;
    private LocalDateTime createdAt;
}

