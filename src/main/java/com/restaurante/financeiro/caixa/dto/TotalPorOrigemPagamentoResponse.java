package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TotalPorOrigemPagamentoResponse {
    private String origem;
    private int quantidade;
    private BigDecimal total = BigDecimal.ZERO;
}

