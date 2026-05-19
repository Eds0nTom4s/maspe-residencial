package com.restaurante.dto.response;

import com.restaurante.model.enums.ChecklistRunStatus;
import com.restaurante.model.enums.ChecklistTipo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChecklistRunResponse {
    private Long id;
    private ChecklistTipo tipo;
    private ChecklistRunStatus status;
    private Long templateId;
    private Long executadoPorUserId;
    private Long dispositivoId;
    private LocalDateTime iniciadoEm;
    private LocalDateTime concluidoEm;
    private List<ChecklistItemRunResponse> itens;
}

