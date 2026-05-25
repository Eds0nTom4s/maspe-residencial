package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.model.enums.InventoryConsumptionStatus;
import com.restaurante.model.enums.InventoryConsumptionTriggerType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryEvidenceConsumptionItemDTO {
    private Long consumptionRecordId;
    private Long pedidoId;
    private Long pagamentoId;
    private InventoryConsumptionStatus status;
    private InventoryConsumptionTriggerType triggerType;
    private LocalDateTime consumedAt;
    private BigDecimal totalCost;
    private BigDecimal netRevenueAmount;
    private BigDecimal estimatedMarginAmount;
    private BigDecimal estimatedMarginPercentage;
    private Integer warningCount;
    private String consumptionHash;
}

