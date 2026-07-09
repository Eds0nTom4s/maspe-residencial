package com.restaurante.dto.response;

import com.restaurante.model.enums.UnitOfMeasureStatus;
import com.restaurante.model.enums.UnitOfMeasureType;
import lombok.Data;

@Data
public class UnitOfMeasureResponse {
    private Long id;
    private String code;
    private String name;
    private UnitOfMeasureType type;
    private Boolean decimalAllowed;
    private UnitOfMeasureStatus status;
}

