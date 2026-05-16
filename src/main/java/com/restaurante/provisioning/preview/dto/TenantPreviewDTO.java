package com.restaurante.provisioning.preview.dto;

import com.restaurante.model.enums.TenantTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPreviewDTO {
    private String nome;
    private String slug;
    private String tenantCode;
    private TenantTipo tipo;
    private boolean tenantCodeGerado;
}

