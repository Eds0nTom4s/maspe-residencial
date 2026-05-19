package com.restaurante.dto.response;

import com.restaurante.model.enums.ChecklistItemRunStatus;
import com.restaurante.model.enums.ChecklistTipoResposta;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChecklistItemRunResponse {
    private Long id;
    private String codigo;
    private String descricao;
    private Boolean obrigatorio;
    private ChecklistTipoResposta tipoResposta;
    private Boolean valorBoolean;
    private String valorTexto;
    private BigDecimal valorNumero;
    private ChecklistItemRunStatus status;
    private String observacao;
    private LocalDateTime respondidoEm;
}

