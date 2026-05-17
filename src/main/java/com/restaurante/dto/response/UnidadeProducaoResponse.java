package com.restaurante.dto.response;

import com.restaurante.model.enums.UnidadeProducaoTipo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnidadeProducaoResponse {
    private Long id;
    private String nome;
    private String codigo;
    private UnidadeProducaoTipo tipo;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Boolean ativo;
    private Integer ordem;
}

