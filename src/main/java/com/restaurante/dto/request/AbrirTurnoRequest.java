package com.restaurante.dto.request;

import com.restaurante.model.enums.TurnoOperacionalTipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AbrirTurnoRequest {
    @NotNull
    private Long instituicaoId;
    @NotNull
    private Long unidadeAtendimentoId;
    @NotNull
    private TurnoOperacionalTipo tipo;
    @NotBlank
    private String nome;
    private String observacao;
    @Valid
    private List<ChecklistItemRespostaRequest> checklist;
}

