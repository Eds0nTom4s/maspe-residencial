package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvidenceBundlePagamentosResumoDTO {
    private Integer quantidadePagamentosConfirmados;
    private Integer quantidadePagamentosPendentes;
    private Integer quantidadeOrdensManuaisConfirmadas;

    private BigDecimal totalGeralConfirmado;
    private BigDecimal totalManualConfirmado;
    private BigDecimal totalGatewayConfirmado;
    private BigDecimal totalPendente;
    private BigDecimal totalFalhado;
    private BigDecimal totalDivergente;
}

