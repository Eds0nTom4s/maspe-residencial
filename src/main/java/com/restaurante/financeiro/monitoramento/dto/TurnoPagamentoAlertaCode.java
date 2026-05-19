package com.restaurante.financeiro.monitoramento.dto;

public enum TurnoPagamentoAlertaCode {
    PAYMENT_PENDING_CALLBACK,
    PAYMENT_POLLING_FAILED,
    PAYMENT_MAX_ATTEMPTS_REACHED,
    PAYMENT_VALUE_DIVERGENCE,
    PAYMENT_OLD_PENDING,
    PAYMENT_GATEWAY_UNKNOWN
}

