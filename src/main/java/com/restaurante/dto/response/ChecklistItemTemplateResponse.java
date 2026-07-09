package com.restaurante.dto.response;

import com.restaurante.model.enums.ChecklistTipoResposta;
import lombok.Data;

@Data
public class ChecklistItemTemplateResponse {
    private Long id;
    private String codigo;
    private String descricao;
    private Boolean obrigatorio;
    private Integer ordem;
    private ChecklistTipoResposta tipoResposta;
}

