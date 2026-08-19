package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class ConfirmarPedidoPaymentOrderRequest {
    @Size(max = 160)
    private String clientRequestId;

    @NotNull
    private MetodoPagamentoManual metodoConfirmado;

    private BigDecimal valorRecebido;

    @Size(max = 200)
    private String referenciaOperador;

    @Size(max = 500)
    private String observacao;
}
