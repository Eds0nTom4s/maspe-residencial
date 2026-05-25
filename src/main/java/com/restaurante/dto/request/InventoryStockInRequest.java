package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryStockInRequest {
    @NotNull
    private BigDecimal quantity;
    @NotBlank
    private String unit;
    @NotNull
    private BigDecimal unitCost;
    private String reason;
    private String reference;
}

