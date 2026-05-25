package com.restaurante.dto.response;

import com.restaurante.model.enums.InventoryRecipeStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryRecipeResponse {
    private Long id;
    private Long productId;
    private String name;
    private InventoryRecipeStatus status;
    private BigDecimal yieldQuantity;
    private String yieldUnit;
}

