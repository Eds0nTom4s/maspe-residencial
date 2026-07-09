package com.restaurante.financeiro.caixa.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TotalPorDeviceResponse {
    private Long deviceId;
    private String deviceNome;
    private String tipoDevice;
    private int quantidadeConfirmacoes;
    private BigDecimal totalCash = BigDecimal.ZERO;
    private BigDecimal totalTpa = BigDecimal.ZERO;
    private BigDecimal totalManual = BigDecimal.ZERO;
    private LocalDateTime ultimoPagamentoEm;
}
