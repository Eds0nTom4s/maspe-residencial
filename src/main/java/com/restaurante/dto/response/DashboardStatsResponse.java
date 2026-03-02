package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para resposta de estatísticas do dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private Integer totalPedidosHoje;
    private Integer pedidosPendentes;
    private BigDecimal receitaHoje;
    private Integer clientesAtivos;
}
