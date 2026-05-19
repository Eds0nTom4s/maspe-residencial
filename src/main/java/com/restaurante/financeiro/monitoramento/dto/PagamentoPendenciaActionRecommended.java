package com.restaurante.financeiro.monitoramento.dto;

public enum PagamentoPendenciaActionRecommended {
    WAIT_CALLBACK,
    WAIT_NEXT_POLL,
    MANUAL_POLL,
    CHECK_GATEWAY,
    CONTACT_SUPPORT,
    REVIEW_DIVERGENCE,
    NONE
}

