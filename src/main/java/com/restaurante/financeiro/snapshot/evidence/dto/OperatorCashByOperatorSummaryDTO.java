package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OperatorCashByOperatorSummaryDTO {
    private Long operadorUserId;
    private Integer totalSessions;
    private Integer closedSessions;
    private Integer reviewedSessions;
    private Integer disputedSessions;

    private BigDecimal expectedCashAmount;
    private BigDecimal declaredCashAmount;
    private BigDecimal cashDifferenceAmount;

    private BigDecimal expectedTpaAmount;
    private BigDecimal declaredTpaAmount;
    private BigDecimal tpaDifferenceAmount;

    private BigDecimal manualDifferenceAmount;
}

