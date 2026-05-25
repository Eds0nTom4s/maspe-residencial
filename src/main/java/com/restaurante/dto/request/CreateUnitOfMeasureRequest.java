package com.restaurante.dto.request;

import com.restaurante.model.enums.UnitOfMeasureType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUnitOfMeasureRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private UnitOfMeasureType type;
    private boolean decimalAllowed;
}

