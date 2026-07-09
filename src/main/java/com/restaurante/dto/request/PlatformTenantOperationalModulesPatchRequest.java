package com.restaurante.dto.request;

import lombok.Data;

@Data
public class PlatformTenantOperationalModulesPatchRequest {

    private Boolean sessaoConsumoEnabled;
    private Boolean pedidoDiretoEnabled;
    private Boolean mesasEnabled;
    private Boolean qrMesaEnabled;
    private Boolean caixaEnabled;
    private Boolean kdsEnabled;
    private String motivo;
}
