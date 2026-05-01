package com.restaurante.store.dto;

import java.math.BigDecimal;

public class StoreAdminResumoDTO {
    private long totalOrdens;
    private long aguardandoPagamento;
    private long pagas;
    private long emSeparacao;
    private long entregues;
    private long canceladas;
    private BigDecimal receitaConfirmada;

    public long getTotalOrdens() { return totalOrdens; }
    public void setTotalOrdens(long totalOrdens) { this.totalOrdens = totalOrdens; }
    public long getAguardandoPagamento() { return aguardandoPagamento; }
    public void setAguardandoPagamento(long aguardandoPagamento) { this.aguardandoPagamento = aguardandoPagamento; }
    public long getPagas() { return pagas; }
    public void setPagas(long pagas) { this.pagas = pagas; }
    public long getEmSeparacao() { return emSeparacao; }
    public void setEmSeparacao(long emSeparacao) { this.emSeparacao = emSeparacao; }
    public long getEntregues() { return entregues; }
    public void setEntregues(long entregues) { this.entregues = entregues; }
    public long getCanceladas() { return canceladas; }
    public void setCanceladas(long canceladas) { this.canceladas = canceladas; }
    public BigDecimal getReceitaConfirmada() { return receitaConfirmada; }
    public void setReceitaConfirmada(BigDecimal receitaConfirmada) { this.receitaConfirmada = receitaConfirmada; }
}
