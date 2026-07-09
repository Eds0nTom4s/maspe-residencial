package com.restaurante.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RotaProducaoResponse {
    private Long id;
    private Long categoriaProdutoId;
    private String categoriaProdutoNome;
    private String categoriaProdutoSlug;
    private Long unidadeProducaoId;
    private String unidadeProducaoNome;
    private String unidadeProducaoCodigo;
    private Boolean ativo;
    private Integer prioridade;
}

