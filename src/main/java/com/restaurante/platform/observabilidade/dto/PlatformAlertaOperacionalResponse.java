package com.restaurante.platform.observabilidade.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformAlertaOperacionalResponse {
    private String alertId;
    private Long tenantId;
    private String tenantNome;
    private PlatformAlertLevel level;
    private PlatformAlertaTipo tipo;
    private String message;
    private String entityType;
    private Long entityId;
    private LocalDateTime createdAt;
    private PlatformActionRecommended actionRecommended;
}

