package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InventoryEvidenceSectionDTO {
    private LocalDateTime generatedAt;
    private Long tenantId;
    private Long turnoId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private Integer totalMovements;
    private Integer stockInCount;
    private Integer saleConsumptionCount;
    private Integer wasteCount;
    private Integer adjustmentCount;

    private BigDecimal totalStockInCost;
    private BigDecimal totalConsumptionCost;
    private BigDecimal totalWasteCost;
    private BigDecimal totalAdjustmentCost;

    private BigDecimal totalRevenue;
    private BigDecimal totalNetRevenue;
    private BigDecimal totalTaxAmount;
    private BigDecimal totalCogs;
    private BigDecimal estimatedGrossMargin;
    private BigDecimal estimatedGrossMarginPercentage;

    private Integer warningCount;
    private List<String> warnings;
    private List<InventoryEvidenceConsumptionItemDTO> consumptionRecords;
}

