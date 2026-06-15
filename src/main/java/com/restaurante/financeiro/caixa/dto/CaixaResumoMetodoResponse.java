package com.restaurante.financeiro.caixa.dto;

import com.restaurante.model.enums.PaymentMethodCode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CaixaResumoMetodoResponse {
    private PaymentMethodCode metodoPagamento;
    private BigDecimal total;
    private long quantidade;
}
