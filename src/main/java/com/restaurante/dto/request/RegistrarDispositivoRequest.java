package com.restaurante.dto.request;

import com.restaurante.model.enums.DispositivoTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrarDispositivoRequest {

    @NotBlank
    @Size(max = 120)
    private String nome;

    @NotBlank
    @Size(min = 3, max = 60)
    private String codigo;

    @NotNull
    private DispositivoTipo tipo;

    @NotNull
    private Long instituicaoId;

    private Long unidadeAtendimentoId;

    private Long unidadeProducaoId;
}
