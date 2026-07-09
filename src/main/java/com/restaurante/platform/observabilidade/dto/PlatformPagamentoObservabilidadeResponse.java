package com.restaurante.platform.observabilidade.dto;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PlatformPagamentoObservabilidadeResponse {
    private Long pagamentoId;
    private Long pedidoId;
    private String numeroPedido;
    private Long turnoId;
    private Long unidadeAtendimentoId;

    private BigDecimal valor;
    private String moeda;
    private MetodoPagamentoAppyPay metodoPagamento;
    private StatusPagamentoGateway statusPagamento;
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

    private long idadeMinutos;
    private PlatformAlertLevel alertLevel;
    private PlatformActionRecommended actionRecommended;
}

