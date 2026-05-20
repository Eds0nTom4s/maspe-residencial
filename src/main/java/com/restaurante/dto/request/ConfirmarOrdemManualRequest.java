package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ConfirmarOrdemManualRequest {

    @NotBlank
    private String clientRequestId;

    @NotNull
    private MetodoPagamentoManual metodoConfirmado;

    private String referenciaOperador;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal valorRecebido;

    private String observacao;

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public MetodoPagamentoManual getMetodoConfirmado() {
        return metodoConfirmado;
    }

    public void setMetodoConfirmado(MetodoPagamentoManual metodoConfirmado) {
        this.metodoConfirmado = metodoConfirmado;
    }

    public String getReferenciaOperador() {
        return referenciaOperador;
    }

    public void setReferenciaOperador(String referenciaOperador) {
        this.referenciaOperador = referenciaOperador;
    }

    public BigDecimal getValorRecebido() {
        return valorRecebido;
    }

    public void setValorRecebido(BigDecimal valorRecebido) {
        this.valorRecebido = valorRecebido;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}

