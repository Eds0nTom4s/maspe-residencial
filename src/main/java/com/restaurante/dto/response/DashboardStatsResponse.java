package com.restaurante.dto.response;

import java.math.BigDecimal;

/**
 * DTO para resposta de estatísticas do dashboard
 */
public class DashboardStatsResponse {
    private Integer totalPedidosHoje;
    private Integer pedidosPendentes;
    private BigDecimal receitaHoje;
    private Integer clientesAtivos;

    public DashboardStatsResponse() {}

    public DashboardStatsResponse(Integer totalPedidosHoje, Integer pedidosPendentes,
                                  BigDecimal receitaHoje, Integer clientesAtivos) {
        this.totalPedidosHoje = totalPedidosHoje;
        this.pedidosPendentes = pedidosPendentes;
        this.receitaHoje = receitaHoje;
        this.clientesAtivos = clientesAtivos;
    }

    public Integer getTotalPedidosHoje() { return totalPedidosHoje; }
    public void setTotalPedidosHoje(Integer totalPedidosHoje) { this.totalPedidosHoje = totalPedidosHoje; }
    public Integer getPedidosPendentes() { return pedidosPendentes; }
    public void setPedidosPendentes(Integer pedidosPendentes) { this.pedidosPendentes = pedidosPendentes; }
    public BigDecimal getReceitaHoje() { return receitaHoje; }
    public void setReceitaHoje(BigDecimal receitaHoje) { this.receitaHoje = receitaHoje; }
    public Integer getClientesAtivos() { return clientesAtivos; }
    public void setClientesAtivos(Integer clientesAtivos) { this.clientesAtivos = clientesAtivos; }

    public static DashboardStatsResponseBuilder builder() { return new DashboardStatsResponseBuilder(); }

    public static class DashboardStatsResponseBuilder {
        private Integer totalPedidosHoje;
        private Integer pedidosPendentes;
        private BigDecimal receitaHoje;
        private Integer clientesAtivos;

        public DashboardStatsResponseBuilder totalPedidosHoje(Integer v) { this.totalPedidosHoje = v; return this; }
        public DashboardStatsResponseBuilder pedidosPendentes(Integer v) { this.pedidosPendentes = v; return this; }
        public DashboardStatsResponseBuilder receitaHoje(BigDecimal v) { this.receitaHoje = v; return this; }
        public DashboardStatsResponseBuilder clientesAtivos(Integer v) { this.clientesAtivos = v; return this; }

        public DashboardStatsResponse build() {
            return new DashboardStatsResponse(totalPedidosHoje, pedidosPendentes, receitaHoje, clientesAtivos);
        }
    }
}
