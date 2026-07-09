package com.restaurante.provisioning.preview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningResourcePlanDTO {
    private int tenantsCriados;
    private int instituicoesCriadas;
    private int unidadesAtendimentoCriadas;
    private int categoriasCriadas;
    private int usuariosCriados;
    private int tenantUsersCriados;
    private int mesasCriadas;
    private int qrCodesCriados;
    private boolean qrPrincipalCriado;
    private int qrPorMesaCriados;
    private boolean overrideLimitesCriado;
}

