package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import jakarta.validation.constraints.NotNull;

public class PublicCriarOrdemPagamentoManualPedidoRequest {

    @NotNull
    private MetodoPagamentoManual metodoPagamento;

    public MetodoPagamentoManual getMetodoPagamento() {
        return metodoPagamento;
    }

    public void setMetodoPagamento(MetodoPagamentoManual metodoPagamento) {
        this.metodoPagamento = metodoPagamento;
    }
}

