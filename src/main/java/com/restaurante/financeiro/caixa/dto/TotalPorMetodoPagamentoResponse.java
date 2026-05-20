package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TotalPorMetodoPagamentoResponse {
    private String metodoPagamento;
    private int quantidade;
    private BigDecimal totalConfirmado = BigDecimal.ZERO;
    private BigDecimal totalPendente = BigDecimal.ZERO;
    private BigDecimal totalFalhado = BigDecimal.ZERO;
}

