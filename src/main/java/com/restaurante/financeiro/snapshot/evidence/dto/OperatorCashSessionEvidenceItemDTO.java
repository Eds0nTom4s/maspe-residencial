package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OperatorCashSessionEvidenceItemDTO {
    private Long caixaId;
    private CaixaOperadorSessionStatus status;
    private Long unidadeId;
    private Long turnoId;
    private Long operationalDeviceId;
    private Long operadorUserId;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime reviewedAt;

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

    private Integer itemsCount;
    private Boolean hasDifference;
    private String differenceSeverity;
    private String sessionHash;

    // Prompt 42.2: divergências/ajustes formais associados ao caixa
    private Integer divergencesCount;
    private Integer unresolvedDivergencesCount;
    private Integer approvedAdjustmentsCount;
    private BigDecimal adjustmentsTotalAmount;
    private Boolean hasUnresolvedDivergence;
    private Boolean hasApprovedAdjustment;
}
