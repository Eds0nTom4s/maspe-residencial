package com.restaurante.provisioning.preview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningLimitLineDTO {
    private String recurso;
    private Integer limite;
    private long usadoAtualmente;
    private int novo;
    private long totalAposProvisionamento;
    private boolean excede;
}

