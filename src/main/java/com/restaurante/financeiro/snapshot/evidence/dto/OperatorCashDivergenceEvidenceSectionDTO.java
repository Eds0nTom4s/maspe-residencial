package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OperatorCashDivergenceEvidenceSectionDTO {
    private LocalDateTime generatedAt;
    private Long tenantId;
    private Long turnoId;

    private Integer totalDivergences;
    private Integer draftDivergences;
    private Integer submittedDivergences;
    private Integer approvedDivergences;
    private Integer rejectedDivergences;
    private Integer unresolvedDivergences;

    private Integer totalAdjustments;
    private Integer approvedAdjustments;
    private BigDecimal totalApprovedAdjustmentAmount;

    private List<OperatorCashDivergenceEvidenceItemDTO> divergenceItems;
    private List<OperatorCashAdjustmentEvidenceItemDTO> adjustmentItems;
}

