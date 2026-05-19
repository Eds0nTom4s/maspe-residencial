package com.restaurante.dto.response;

import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TurnoOperacionalResponse {
    private Long id;
    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private TurnoOperacionalStatus status;
    private TurnoOperacionalTipo tipo;
    private String nome;
    private Long abertoPorUserId;
    private Long fechadoPorUserId;
    private LocalDateTime abertoEm;
    private LocalDateTime fechadoEm;
    private String observacaoAbertura;
    private String observacaoFecho;
    private String resumoJson;

    private List<ChecklistRunResponse> checklists;
}

