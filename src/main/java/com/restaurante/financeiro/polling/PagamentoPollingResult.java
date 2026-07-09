package com.restaurante.financeiro.polling;

import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PagamentoPollingResult {
    private Long pagamentoId;
    private StatusPagamentoGateway statusAnterior;
    private StatusPagamentoGateway statusAtual;
    private PagamentoPollingStatus pollingStatus;
    private GatewayPaymentStatus gatewayStatus;
    private boolean confirmado;
    private int attempts;
    private String message;
    private String errorCode;
}

