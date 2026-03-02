package com.restaurante.service;

import com.restaurante.dto.response.DashboardActivityResponse;
import com.restaurante.dto.response.DashboardStatsResponse;
import com.restaurante.dto.response.DashboardTopProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service para operações de dashboard
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    /**
     * Retorna estatísticas gerais do dashboard
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        log.debug("Buscando estatísticas gerais do dashboard");

        // Retorna dados mockados temporariamente
        return DashboardStatsResponse.builder()
                .totalPedidosHoje(25)
                .pedidosPendentes(5)
                .receitaHoje(BigDecimal.valueOf(1250.50))
                .clientesAtivos(12)
                .build();
    }

    /**
     * Retorna atividades recentes do sistema
     */
    @Transactional(readOnly = true)
    public List<DashboardActivityResponse> getRecentActivity(int limit) {
        log.debug("Buscando {} atividades recentes", limit);

        // Retorna dados mockados temporariamente
        List<DashboardActivityResponse> activities = new ArrayList<>();
        
        for (int i = 0; i < Math.min(limit, 5); i++) {
            DashboardActivityResponse activity = new DashboardActivityResponse();
            activity.setTipo("PEDIDO");
            activity.setDescricao("Pedido criado - Mesa " + (i + 1));
            activity.setTimestamp(LocalDateTime.now().minusMinutes(i * 10));
            activity.setDetalhes("Aguardando processamento");
            activities.add(activity);
        }
        
        return activities;
    }

    /**
     * Retorna produtos mais vendidos
     */
    @Transactional(readOnly = true)
    public List<DashboardTopProductResponse> getTopProducts(int limit) {
        log.debug("Buscando {} produtos mais vendidos", limit);

        // Retorna dados mockados temporariamente
        List<DashboardTopProductResponse> products = new ArrayList<>();
        
        for (int i = 0; i < Math.min(limit, 5); i++) {
            DashboardTopProductResponse product = new DashboardTopProductResponse();
            product.setProdutoId((long) (i + 1));
            product.setNome("Produto " + (i + 1));
            product.setQuantidadeVendida(100 - (i * 10));
            product.setValorTotal(BigDecimal.valueOf(500 - (i * 50)));
            product.setCategoria("CATEGORIA_" + (i + 1));
            products.add(product);
        }
        
        return products;
    }
}
