package com.restaurante.provisioning.preview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionarTenantPreviewResponse {

    private boolean permitido;
    private List<ProvisioningValidationMessage> bloqueios;
    private List<ProvisioningValidationMessage> avisos;

    private ProvisioningResourcePlanDTO recursosPlanejados;
    private ProvisioningLimitsPreviewDTO limites;
    private ProvisioningTemplatePreviewDTO template;
    private TenantPreviewDTO tenant;
    private InstituicaoPreviewDTO instituicao;
    private OwnerPreviewDTO owner;
    private ProvisioningEstimateDTO estimativa;
}

