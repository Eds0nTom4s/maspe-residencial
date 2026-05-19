package com.restaurante.financeiro.monitoramento.dto;

import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PagamentoPollingEventoRecenteResponse {
    private Long eventId;
    private OperationalEventType eventType;
    private OperationalOrigem origem;
    private String motivo;
    private LocalDateTime createdAt;
}

