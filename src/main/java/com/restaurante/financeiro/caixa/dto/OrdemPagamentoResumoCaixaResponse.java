package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrdemPagamentoResumoCaixaResponse {
    private Long ordemPagamentoId;
    private String tipo;
    private String metodoSolicitado;
    private String status;
    private BigDecimal valor;
    private String moeda;
    private Long pedidoId;
    private Long fundoConsumoId;
    private Long sessaoConsumoId;
    private LocalDateTime criadoEm;
    private LocalDateTime confirmadoEm;
    private Long confirmadoPorDeviceId;
}

