package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventoOperacionalTurnoResponse {
    private Long eventId;
    private String eventType;
    private String entityType;
    private Long entityId;
    private Long pedidoId;
    private String pedidoNumero;
    private Long subPedidoId;
    private String actorType;
    private Long actorUserId;
    private Long deviceId;
    private String origem;
    private String statusAnterior;
    private String statusNovo;
    private String resumo;
    private LocalDateTime createdAt;
}
