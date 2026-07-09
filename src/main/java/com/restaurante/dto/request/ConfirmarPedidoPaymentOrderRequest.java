package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import lombok.Data;

@Data
public class ConfirmarPedidoPaymentOrderRequest {
    private MetodoPagamentoManual metodoConfirmado;
    private String referenciaOperador;
    private String observacao;
}
