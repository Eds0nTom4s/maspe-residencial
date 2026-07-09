package com.restaurante.financeiro.enums;

public enum PagamentoPollingStatus {
    ELIGIBLE,
    IN_PROGRESS,
    CONFIRMED_BY_POLLING,
    EXPIRED,
    FAILED,
    MAX_ATTEMPTS_REACHED,
    DISABLED
}

