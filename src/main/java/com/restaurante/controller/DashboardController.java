package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DashboardActivityResponse;
import com.restaurante.dto.response.DashboardStatsResponse;
import com.restaurante.dto.response.DashboardTopProductResponse;
import com.restaurante.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para endpoints do dashboard administrativo
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Retorna estatísticas gerais do dashboard
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        log.info("📊 Buscando estatísticas do dashboard");
        
        DashboardStatsResponse stats = dashboardService.getStats();
        
        log.info("✅ Estatísticas recuperadas");
        return ResponseEntity.ok(ApiResponse.success("Estatísticas recuperadas com sucesso", stats));
    }

    /**
     * Retorna atividades recentes do sistema
     */
    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public ResponseEntity<ApiResponse<List<DashboardActivityResponse>>> getRecentActivity(
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("📊 Buscando atividades recentes do dashboard (limit={})", limit);
        
        List<DashboardActivityResponse> activities = dashboardService.getRecentActivity(limit);
        
        log.info("✅ {} atividades encontradas", activities.size());
        return ResponseEntity.ok(ApiResponse.success("Atividades recuperadas com sucesso", activities));
    }

    /**
     * Retorna produtos mais vendidos
     */
    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ApiResponse<List<DashboardTopProductResponse>>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("📊 Buscando produtos mais vendidos (limit={})", limit);
        
        List<DashboardTopProductResponse> topProducts = dashboardService.getTopProducts(limit);
        
        log.info("✅ {} produtos encontrados", topProducts.size());
        return ResponseEntity.ok(ApiResponse.success("Produtos mais vendidos recuperados com sucesso", topProducts));
    }
}
