package com.restaurante.dto.request;

import com.restaurante.model.enums.BusinessAccountEstado;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountEstadoUpdateRequest {

    @NotNull
    private BusinessAccountEstado estado;

    private String motivo;
}
