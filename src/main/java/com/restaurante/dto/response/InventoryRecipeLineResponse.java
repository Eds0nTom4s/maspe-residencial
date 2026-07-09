package com.restaurante.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryRecipeLineResponse {
    private Long id;
    private Long recipeId;
    private Long inventoryItemId;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal wastePercentage;
    private BigDecimal costSnapshot;
}

