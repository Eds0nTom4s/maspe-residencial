package com.restaurante.dto.response;

import com.restaurante.model.enums.InventoryItemStatus;
import com.restaurante.model.enums.InventoryItemType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryItemResponse {
    private Long id;
    private String name;
    private String sku;
    private InventoryItemType type;
    private String category;
    private String baseUnitCode;
    private Boolean stockControlEnabled;
    private Boolean allowNegativeStock;
    private BigDecimal currentQuantity;
    private BigDecimal averageCost;
    private BigDecimal lastCost;
    private BigDecimal minimumQuantity;
    private BigDecimal reorderQuantity;
    private InventoryItemStatus status;
}

