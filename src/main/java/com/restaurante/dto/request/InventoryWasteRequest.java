package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryWasteRequest {
    @NotNull
    private BigDecimal quantity;
    @NotBlank
    private String unit;
    @NotBlank
    private String reason;
}

