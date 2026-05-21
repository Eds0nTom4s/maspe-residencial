package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

@Data
public class EvidenceBundleTenantDTO {
    private Long tenantId;
    private String tenantNome;
    private String tenantCode;
}

