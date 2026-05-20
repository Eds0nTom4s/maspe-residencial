package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CriarCarregamentoFundoRequest {

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal valor;

    @NotNull
    private MetodoPagamentoManual metodoPagamento;

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public MetodoPagamentoManual getMetodoPagamento() {
        return metodoPagamento;
    }

    public void setMetodoPagamento(MetodoPagamentoManual metodoPagamento) {
        this.metodoPagamento = metodoPagamento;
    }
}

