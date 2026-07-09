package com.restaurante.dto.response;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DevicePagamentoResponse {
    private Long pagamentoId;
    private Long pedidoId;
    private String numeroPedido;
    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long turnoOperacionalId;
    private BigDecimal valor;
    private String moeda;
    private MetodoPagamentoAppyPay metodoPagamento;
    private StatusPagamentoGateway statusPagamento;
    private String gateway;
    private String externalReference;
    private String checkoutUrl;
    private String referencia;
    private String entidade;
    private LocalDateTime expiresAt;
    private boolean idempotentReplay;
    private LocalDateTime criadoEm;
}

