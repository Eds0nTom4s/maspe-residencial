package com.restaurante.financeiro.caixa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PedidoExtratoTurnoResponse {
    private Long pedidoId;
    private String numero;
    private String origem;
    private String statusOperacional;
    private String statusFinanceiro;
    private BigDecimal total;
    private Integer quantidadeItens;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime pagoEm;
}
