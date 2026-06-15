package com.restaurante.financeiro.caixa.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CaixaResumoResponse {
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private BigDecimal totalPendente;
    private BigDecimal totalPago;
    private long quantidadePendente;
    private long quantidadePago;
    private List<CaixaResumoMetodoResponse> porMetodo;
}
