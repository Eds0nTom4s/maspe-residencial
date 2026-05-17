package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelecionarMinhaUnidadeRequest {

    @NotNull
    private Long unidadeProducaoId;
}

