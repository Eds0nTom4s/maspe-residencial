package com.restaurante.dto.response;

import com.restaurante.model.enums.InventoryConsumptionStatus;
import com.restaurante.model.enums.InventoryConsumptionTriggerType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryConsumptionRecordResponse {
    private Long id;
    private Long pedidoId;
    private Long pagamentoId;
    private InventoryConsumptionStatus status;
    private InventoryConsumptionTriggerType triggerType;
    private LocalDateTime consumedAt;
    private BigDecimal grossRevenueAmount;
    private BigDecimal netRevenueAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalCost;
    private BigDecimal estimatedMarginAmount;
    private BigDecimal estimatedMarginPercentage;
    private Integer warningCount;
}

