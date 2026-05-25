package com.restaurante.dto.response;

import com.restaurante.model.enums.InventoryRestockPolicy;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryReturnLineResponse {
    private Long id;
    private Long tenantId;
    private Long pedidoItemId;
    private Long productId;
    private Long inventoryConsumptionLineId;
    private Long inventoryItemId;
    private Long recipeId;
    private BigDecimal quantityReturned;
    private Long unitId;
    private BigDecimal quantityBaseUnit;
    private BigDecimal unitCost;
    private BigDecimal totalCostReversed;
    private BigDecimal stockBefore;
    private BigDecimal stockAfter;
    private InventoryRestockPolicy restockPolicy;
    private Long movementId;
    private String warningCode;
}

