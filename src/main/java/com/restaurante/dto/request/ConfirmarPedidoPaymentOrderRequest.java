package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfirmarPedidoPaymentOrderRequest {
    @NotBlank
    @Size(max = 160)
    private String clientRequestId;

    @NotNull
    private MetodoPagamentoManual metodoConfirmado;

    @Size(max = 200)
    private String referenciaOperador;

    @Size(max = 500)
    private String observacao;
}
