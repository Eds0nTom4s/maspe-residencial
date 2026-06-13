package com.restaurante.dto.response.kds;

import com.restaurante.model.enums.UnidadeProducaoTipo;

public record KdsUnidadeProducaoResponse(
        Long id,
        String nome,
        UnidadeProducaoTipo tipo,
        Boolean ativo,
        Integer ordem
) {
}
