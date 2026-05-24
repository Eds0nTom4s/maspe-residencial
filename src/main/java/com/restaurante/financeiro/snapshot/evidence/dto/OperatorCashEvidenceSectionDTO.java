package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OperatorCashEvidenceSectionDTO {
    private String sourceVersion;
    private LocalDateTime generatedAt;

    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeId;
    private Long turnoId;

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private Integer totalCashSessions;
    private Integer openCashSessions;
    private Integer closedCashSessions;
    private Integer reviewedCashSessions;
    private Integer disputedCashSessions;
    private Integer cancelledCashSessions;

    private BigDecimal expectedCashAmount;
    private BigDecimal declaredCashAmount;
    private BigDecimal cashDifferenceAmount;

    private BigDecimal expectedTpaAmount;
    private BigDecimal declaredTpaAmount;
    private BigDecimal tpaDifferenceAmount;

    private BigDecimal expectedManualTotalAmount;
    private BigDecimal declaredManualTotalAmount;
    private BigDecimal manualDifferenceAmount;

    private BigDecimal expectedAppyPayAmount;

    private List<OperatorCashSessionEvidenceItemDTO> sessions;
    private List<OperatorCashByOperatorSummaryDTO> byOperator;
    private List<OperatorCashByDeviceSummaryDTO> byDevice;
    private List<String> warnings;
}

