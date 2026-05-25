package com.restaurante.dto.response;

import com.restaurante.model.enums.InventoryReturnReasonCategory;
import com.restaurante.model.enums.InventoryReturnSource;
import com.restaurante.model.enums.InventoryReturnStatus;
import com.restaurante.model.enums.InventoryReturnType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryReturnRecordResponse {
    private Long id;
    private Long tenantId;
    private Long unidadeAtendimentoId;
    private Long pedidoId;
    private Long pagamentoId;
    private Long fiscalDocumentId;
    private Long fiscalCorrectionDocumentId;
    private Long inventoryConsumptionRecordId;
    private InventoryReturnType returnType;
    private InventoryReturnStatus status;
    private InventoryReturnSource source;
    private InventoryReturnReasonCategory reasonCategory;
    private String reasonDescription;
    private Long requestedByUserId;
    private Long approvedByUserId;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime processedAt;
    private BigDecimal totalReturnCost;
    private BigDecimal totalRevenueReversed;
    private BigDecimal totalTaxReversed;
    private BigDecimal totalMarginReversed;
    private Integer warningCount;
    private LocalDateTime createdAt;
}

