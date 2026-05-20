package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResumoCaixaTurnoMiniResponse {
    private BigDecimal totalGeralConfirmado = BigDecimal.ZERO;
    private BigDecimal totalCash = BigDecimal.ZERO;
    private BigDecimal totalTpa = BigDecimal.ZERO;
    private BigDecimal totalAppyPay = BigDecimal.ZERO;
    private BigDecimal totalPendente = BigDecimal.ZERO;
    private BigDecimal totalCarregamentoFundo = BigDecimal.ZERO;
    private BigDecimal totalPagamentoPedidos = BigDecimal.ZERO;
}

