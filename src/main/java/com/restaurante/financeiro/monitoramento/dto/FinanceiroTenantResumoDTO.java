package com.restaurante.financeiro.monitoramento.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FinanceiroTenantResumoDTO {

    private long totalPagamentosHoje;
    private long totalConfirmadoHoje;
    private long totalPendente;
    private BigDecimal totalPendenteValor;
    private long pagamentosPendentesAntigos;
    private long callbacksInvalidosHoje;
    private long callbacksPaymentNotFoundHoje;
    private long divergenciasValorHoje;
    private LocalDateTime ultimoCallbackRecebidoEm;

    public long getTotalPagamentosHoje() { return totalPagamentosHoje; }
    public void setTotalPagamentosHoje(long totalPagamentosHoje) { this.totalPagamentosHoje = totalPagamentosHoje; }

    public long getTotalConfirmadoHoje() { return totalConfirmadoHoje; }
    public void setTotalConfirmadoHoje(long totalConfirmadoHoje) { this.totalConfirmadoHoje = totalConfirmadoHoje; }

    public long getTotalPendente() { return totalPendente; }
    public void setTotalPendente(long totalPendente) { this.totalPendente = totalPendente; }

    public BigDecimal getTotalPendenteValor() { return totalPendenteValor; }
    public void setTotalPendenteValor(BigDecimal totalPendenteValor) { this.totalPendenteValor = totalPendenteValor; }

    public long getPagamentosPendentesAntigos() { return pagamentosPendentesAntigos; }
    public void setPagamentosPendentesAntigos(long pagamentosPendentesAntigos) { this.pagamentosPendentesAntigos = pagamentosPendentesAntigos; }

    public long getCallbacksInvalidosHoje() { return callbacksInvalidosHoje; }
    public void setCallbacksInvalidosHoje(long callbacksInvalidosHoje) { this.callbacksInvalidosHoje = callbacksInvalidosHoje; }

    public long getCallbacksPaymentNotFoundHoje() { return callbacksPaymentNotFoundHoje; }
    public void setCallbacksPaymentNotFoundHoje(long callbacksPaymentNotFoundHoje) { this.callbacksPaymentNotFoundHoje = callbacksPaymentNotFoundHoje; }

    public long getDivergenciasValorHoje() { return divergenciasValorHoje; }
    public void setDivergenciasValorHoje(long divergenciasValorHoje) { this.divergenciasValorHoje = divergenciasValorHoje; }

    public LocalDateTime getUltimoCallbackRecebidoEm() { return ultimoCallbackRecebidoEm; }
    public void setUltimoCallbackRecebidoEm(LocalDateTime ultimoCallbackRecebidoEm) { this.ultimoCallbackRecebidoEm = ultimoCallbackRecebidoEm; }
}

