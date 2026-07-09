package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.model.enums.InventoryReturnSource;
import com.restaurante.model.enums.InventoryReturnStatus;
import com.restaurante.model.enums.InventoryReturnType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryEvidenceReturnItemDTO {
    private Long returnId;
    private Long pedidoId;
    private Long pagamentoId;
    private InventoryReturnStatus status;
    private InventoryReturnType returnType;
    private InventoryReturnSource source;
    private BigDecimal totalReturnCost;
    private BigDecimal totalRevenueReversed;
    private BigDecimal totalTaxReversed;
    private BigDecimal totalMarginReversed;
    private LocalDateTime processedAt;
    private Integer warningCount;
    private String returnHash;
}

