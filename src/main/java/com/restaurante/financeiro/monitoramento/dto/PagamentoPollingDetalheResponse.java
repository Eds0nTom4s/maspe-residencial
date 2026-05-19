package com.restaurante.financeiro.monitoramento.dto;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PagamentoPollingDetalheResponse {
    private Long pagamentoId;
    private Long pedidoId;
    private String numeroPedido;
    private StatusPagamentoGateway statusPagamento;
    private BigDecimal valor;
    private String moeda;
    private MetodoPagamentoAppyPay metodoPagamento;
    private String externalReference;
    private String gatewayTransactionId;

    private boolean pollingEnabled;
    private PagamentoPollingStatus pollingStatus;
    private int pollingAttempts;
    private LocalDateTime lastPollingAttemptAt;
    private LocalDateTime nextPollingAttemptAt;
    private LocalDateTime gatewayStatusLastCheckedAt;
    private String pollingLastErrorCode;
    private String pollingLastErrorMessage;
    private LocalDateTime expiresAt;

    private boolean canManualPoll;
    private String manualPollBlockedReason;

    private List<PagamentoPollingEventoRecenteResponse> eventosRecentes;
}

