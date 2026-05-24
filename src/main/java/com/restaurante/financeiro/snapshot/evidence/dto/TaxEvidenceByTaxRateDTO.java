package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxEvidenceByTaxRateDTO {
    private String taxRateCode;
    private BigDecimal taxableAmount;
    private BigDecimal exemptAmount;
    private BigDecimal taxAmount;
    private BigDecimal grossAmount;
    private Integer documentsCount;
}

