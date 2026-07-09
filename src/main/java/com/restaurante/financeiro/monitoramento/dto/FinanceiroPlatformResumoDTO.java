package com.restaurante.financeiro.monitoramento.dto;

import java.math.BigDecimal;

public class FinanceiroPlatformResumoDTO {

    private long totalTenantsComPagamentos;
    private long totalPagamentosHoje;
    private long totalConfirmadoHoje;
    private long totalPendente;
    private long totalPendentesAntigos;
    private long totalCallbacksInvalidos;
    private long totalPaymentNotFound;
    private long totalDivergencias;
    private BigDecimal valorConfirmadoHoje;

    public long getTotalTenantsComPagamentos() { return totalTenantsComPagamentos; }
    public void setTotalTenantsComPagamentos(long totalTenantsComPagamentos) { this.totalTenantsComPagamentos = totalTenantsComPagamentos; }

    public long getTotalPagamentosHoje() { return totalPagamentosHoje; }
    public void setTotalPagamentosHoje(long totalPagamentosHoje) { this.totalPagamentosHoje = totalPagamentosHoje; }

    public long getTotalConfirmadoHoje() { return totalConfirmadoHoje; }
    public void setTotalConfirmadoHoje(long totalConfirmadoHoje) { this.totalConfirmadoHoje = totalConfirmadoHoje; }

    public long getTotalPendente() { return totalPendente; }
    public void setTotalPendente(long totalPendente) { this.totalPendente = totalPendente; }

    public long getTotalPendentesAntigos() { return totalPendentesAntigos; }
    public void setTotalPendentesAntigos(long totalPendentesAntigos) { this.totalPendentesAntigos = totalPendentesAntigos; }

    public long getTotalCallbacksInvalidos() { return totalCallbacksInvalidos; }
    public void setTotalCallbacksInvalidos(long totalCallbacksInvalidos) { this.totalCallbacksInvalidos = totalCallbacksInvalidos; }

    public long getTotalPaymentNotFound() { return totalPaymentNotFound; }
    public void setTotalPaymentNotFound(long totalPaymentNotFound) { this.totalPaymentNotFound = totalPaymentNotFound; }

    public long getTotalDivergencias() { return totalDivergencias; }
    public void setTotalDivergencias(long totalDivergencias) { this.totalDivergencias = totalDivergencias; }

    public BigDecimal getValorConfirmadoHoje() { return valorConfirmadoHoje; }
    public void setValorConfirmadoHoje(BigDecimal valorConfirmadoHoje) { this.valorConfirmadoHoje = valorConfirmadoHoje; }
}

