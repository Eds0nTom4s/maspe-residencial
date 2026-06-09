package com.restaurante.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantOperationalModulesResponse {

    private Long tenantId;
    private boolean sessaoConsumoEnabled;
    private boolean pedidoDiretoEnabled;
    private boolean mesasEnabled;
    private boolean qrMesaEnabled;
    private boolean caixaEnabled;
    private boolean kdsEnabled;
    private Long configuredByPlatformUserId;
    private LocalDateTime configuredAt;
}
