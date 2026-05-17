package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfigurarRotaProducaoRequest {
    @NotNull
    private Long categoriaProdutoId;
    @NotNull
    private Long unidadeProducaoId;
    private Integer prioridade;
}

