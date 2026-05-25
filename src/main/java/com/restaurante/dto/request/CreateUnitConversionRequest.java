package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateUnitConversionRequest {
    @NotNull
    private Long fromUnitId;
    @NotNull
    private Long toUnitId;
    @NotNull
    private BigDecimal factor;
}

