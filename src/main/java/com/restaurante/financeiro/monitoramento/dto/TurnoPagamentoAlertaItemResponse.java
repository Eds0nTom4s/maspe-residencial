package com.restaurante.financeiro.monitoramento.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TurnoPagamentoAlertaItemResponse {
    private TurnoPagamentoAlertaCode code;
    private String message;
    private TurnoPagamentoAlertaLevel level;
    private PagamentoPendenciaActionRecommended actionRecommended;
    private Long pagamentoId;
    private Long pedidoId;
    private BigDecimal valor;
    private long idadeMinutos;
}

