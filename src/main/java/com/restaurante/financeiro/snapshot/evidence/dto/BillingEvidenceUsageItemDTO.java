package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillingEvidenceUsageItemDTO {
    private String metricCode;
    private BigDecimal quantityTotal;
    private BigDecimal includedQuantity;
    private BigDecimal overageQuantity;
    private BigDecimal calculatedChargeAmount;
    private String aggregationHash;
}

