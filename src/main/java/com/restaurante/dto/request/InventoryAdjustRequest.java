package com.restaurante.dto.request;

import com.restaurante.model.enums.InventoryMovementDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryAdjustRequest {
    @NotNull
    private InventoryMovementDirection direction;
    @NotNull
    private BigDecimal quantity;
    @NotBlank
    private String unit;
    @NotBlank
    private String reason;
}

