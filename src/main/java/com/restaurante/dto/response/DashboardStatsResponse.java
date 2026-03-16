package com.restaurante.dto.response;

import java.math.BigDecimal;

/**
 * DTO para resposta de estatísticas do dashboard.
 * Todos os campos refletem dados reais do banco de dados.
 */
public class DashboardStatsResponse {

    // Pedidos
    private Long totalPedidosHoje;
    private Long pedidosPendentes;       // CRIADO ou EM_ANDAMENTO
    private BigDecimal receitaHoje;      // Soma dos totais dos pedidos de hoje

    // Sessões
    private Long sessoesAbertas;                // status = ABERTA
    private Long sessoesAguardandoPagamento;    // status = AGUARDANDO_PAGAMENTO

    // Clientes
    private Long clientesAtendidosHoje;         // Sessões abertas hoje com cliente identificado

    public DashboardStatsResponse() {}

    // Getters e Setters
    public Long getTotalPedidosHoje() { return totalPedidosHoje; }
    public void setTotalPedidosHoje(Long totalPedidosHoje) { this.totalPedidosHoje = totalPedidosHoje; }
    public Long getPedidosPendentes() { return pedidosPendentes; }
    public void setPedidosPendentes(Long pedidosPendentes) { this.pedidosPendentes = pedidosPendentes; }
    public BigDecimal getReceitaHoje() { return receitaHoje; }
    public void setReceitaHoje(BigDecimal receitaHoje) { this.receitaHoje = receitaHoje; }
    public Long getSessoesAbertas() { return sessoesAbertas; }
    public void setSessoesAbertas(Long sessoesAbertas) { this.sessoesAbertas = sessoesAbertas; }
    public Long getSessoesAguardandoPagamento() { return sessoesAguardandoPagamento; }
    public void setSessoesAguardandoPagamento(Long sessoesAguardandoPagamento) { this.sessoesAguardandoPagamento = sessoesAguardandoPagamento; }
    public Long getClientesAtendidosHoje() { return clientesAtendidosHoje; }
    public void setClientesAtendidosHoje(Long clientesAtendidosHoje) { this.clientesAtendidosHoje = clientesAtendidosHoje; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final DashboardStatsResponse obj = new DashboardStatsResponse();
        public Builder totalPedidosHoje(Long v)              { obj.totalPedidosHoje = v; return this; }
        public Builder pedidosPendentes(Long v)              { obj.pedidosPendentes = v; return this; }
        public Builder receitaHoje(BigDecimal v)             { obj.receitaHoje = v; return this; }
        public Builder sessoesAbertas(Long v)                { obj.sessoesAbertas = v; return this; }
        public Builder sessoesAguardandoPagamento(Long v)   { obj.sessoesAguardandoPagamento = v; return this; }
        public Builder clientesAtendidosHoje(Long v)         { obj.clientesAtendidosHoje = v; return this; }
        public DashboardStatsResponse build()                { return obj; }
    }
}
