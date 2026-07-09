package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventoFinanceiroTurnoResponse {
    private Long eventId;
    private String eventType;
    private String entityType;
    private Long entityId;
    private String actorType;
    private Long actorUserId;
    private Long deviceId;
    private String origem;
    private LocalDateTime createdAt;
    private String resumo;
}

