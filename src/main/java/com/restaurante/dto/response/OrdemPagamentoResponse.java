package com.restaurante.dto.response;

import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrdemPagamentoResponse {
    private Long ordemPagamentoId;
    private OrdemPagamentoTipo tipo;
    private OrdemPagamentoStatus status;
    private BigDecimal valor;
    private String moeda;
    private MetodoPagamentoManual metodoPagamento;
    private String ordemToken;
    private String qrOrdemPagamento;
    private LocalDateTime expiresAt;
    private String mensagem;
}

