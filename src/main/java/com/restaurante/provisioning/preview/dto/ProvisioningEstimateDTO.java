package com.restaurante.provisioning.preview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningEstimateDTO {
    private int totalQrUrlsGeradas;
    private int totalMesas;
    private boolean precisaOverride;
    private boolean prontoParaExecutar;
}

