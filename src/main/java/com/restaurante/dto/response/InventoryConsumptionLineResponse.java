package com.restaurante.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryConsumptionLineResponse {
    private Long id;
    private Long inventoryItemId;
    private Long productId;
    private Long pedidoItemId;
    private Long recipeId;
    private BigDecimal quantityBaseUnit;
    private String unit;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private BigDecimal stockBefore;
    private BigDecimal stockAfter;
    private String warningCode;
}

