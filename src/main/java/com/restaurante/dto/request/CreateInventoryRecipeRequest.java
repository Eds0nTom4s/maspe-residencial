package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInventoryRecipeRequest {
    @NotNull
    private Long productId;
    @NotBlank
    private String name;
    private BigDecimal yieldQuantity;
    @NotBlank
    private String yieldUnit;
}

