package com.restaurante.dto.response;

import com.restaurante.model.enums.UnitConversionStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UnitConversionResponse {
    private Long id;
    private Long fromUnitId;
    private Long toUnitId;
    private BigDecimal factor;
    private UnitConversionStatus status;
}

