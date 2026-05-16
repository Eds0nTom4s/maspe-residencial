package com.restaurante.dto.response;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicQrPagamentoResponse {

    private Long pagamentoId;
    private Long pedidoId;
    private String pedidoNumero;

    private String externalReference;
    private StatusPagamentoGateway statusPagamento;
    private MetodoPagamentoAppyPay metodoPagamento;

    private BigDecimal valor;
    private String entidade;
    private String referencia;
    private String paymentUrl;

    private String mensagem;
}

