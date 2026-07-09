package com.restaurante.dto.response;

import com.restaurante.model.enums.ChecklistEscopo;
import com.restaurante.model.enums.ChecklistTipo;
import lombok.Data;

import java.util.List;

@Data
public class ChecklistTemplateResponse {
    private Long id;
    private ChecklistTipo tipo;
    private String nome;
    private ChecklistEscopo escopo;
    private Boolean ativo;
    private List<ChecklistItemTemplateResponse> itens;
}

