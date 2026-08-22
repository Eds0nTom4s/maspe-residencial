package com.restaurante.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.restaurante.model.enums.UnidadeProducaoTipo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AtualizarUnidadeProducaoRequest {

    private Long unidadeAtendimentoId;
    @JsonIgnore
    private boolean unidadeAtendimentoIdInformado;

    @JsonSetter("unidadeAtendimentoId")
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) {
        this.unidadeAtendimentoId = unidadeAtendimentoId;
        this.unidadeAtendimentoIdInformado = true;
    }

    @Size(min = 1, max = 120)
    private String nome;

    private UnidadeProducaoTipo tipo;

    @Min(0)
    private Integer ordem;
}
