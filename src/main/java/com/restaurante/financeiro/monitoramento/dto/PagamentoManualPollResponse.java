package com.restaurante.financeiro.monitoramento.dto;

import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.polling.GatewayPaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagamentoManualPollResponse {
    private Long pagamentoId;
    private Long pedidoId;
    private StatusPagamentoGateway statusAnterior;
    private StatusPagamentoGateway statusAtual;
    private PagamentoPollingStatus pollingStatus;
    private GatewayPaymentStatus gatewayStatus;
    private boolean confirmado;
    private BigDecimal valor;
    private String moeda;
    private int attempts;
    private String message;
    private Long eventId;
    private LocalDateTime executadoEm;
}

