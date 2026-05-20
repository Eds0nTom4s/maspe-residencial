package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagamentoResumoCaixaResponse {
    private Long pagamentoId;
    private Long pedidoId;
    private String metodoPagamento;
    private String origem;
    private String status;
    private BigDecimal valor;
    private String moeda;
    private LocalDateTime confirmadoEm;
    private String externalReference;
}

