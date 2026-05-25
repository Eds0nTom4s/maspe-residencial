package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddInventoryRecipeLineRequest {
    @NotNull
    private Long inventoryItemId;
    @NotNull
    private BigDecimal quantity;
    @NotBlank
    private String unit;
    private BigDecimal wastePercentage;
}

