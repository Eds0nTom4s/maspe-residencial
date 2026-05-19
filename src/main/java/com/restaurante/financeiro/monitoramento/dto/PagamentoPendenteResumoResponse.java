package com.restaurante.financeiro.monitoramento.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PagamentoPendenteResumoResponse {
    private long totalPendentes;
    private BigDecimal valorTotalPendente;
    private long totalPollingEligible;
    private long totalPollingInProgress;
    private long totalMaxAttemptsReached;
    private long totalComErroGateway;
    private long totalCriticos;
    private long totalWarnings;
    private LocalDateTime maisAntigoPendenteEm;
    private Map<String, Long> porPollingStatus;
    private Map<String, Long> porMetodoPagamento;
    private Map<Long, Long> porUnidadeAtendimento;
    private Map<Long, Long> porTurno;
}

