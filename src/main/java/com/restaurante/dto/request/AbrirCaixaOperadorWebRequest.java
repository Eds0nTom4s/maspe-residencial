package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AbrirCaixaOperadorWebRequest {
    @NotNull
    private Long instituicaoId;

    @NotNull
    private Long unidadeAtendimentoId;

    private Long turnoId;

    @Size(max = 500)
    private String notes;
}
