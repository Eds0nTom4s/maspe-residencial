package com.restaurante.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FecharTurnoRequest {
    private String observacao;
    @NotNull
    private Boolean forcarFecho = false;
    @Valid
    private List<ChecklistItemRespostaRequest> checklist;
}

