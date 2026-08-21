package com.restaurante.dto.request;

import com.restaurante.model.enums.UnidadeProducaoTipo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CriarUnidadeProducaoRequest {

    @NotNull
    private Long instituicaoId;

    private Long unidadeAtendimentoId;

    @NotBlank
    @Size(max = 120)
    private String nome;

    @NotBlank
    @Size(max = 40)
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Código deve conter apenas letras, números, hífen ou underscore.")
    private String codigo;

    @NotNull
    private UnidadeProducaoTipo tipo;

    @Min(0)
    private Integer ordem;
}
