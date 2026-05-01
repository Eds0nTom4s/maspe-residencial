package com.restaurante.store.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.store.dto.StoreProductDTO;
import com.restaurante.store.service.StoreAnalyticsService;
import com.restaurante.store.service.StoreCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/store/catalogo")
public class StoreCatalogController {

    private final StoreCatalogService catalogService;
    private final StoreAnalyticsService analyticsService;

    public StoreCatalogController(StoreCatalogService catalogService, StoreAnalyticsService analyticsService) {
        this.catalogService = catalogService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreProductDTO>>> listCatalog() {
        return ResponseEntity.ok(ApiResponse.success("Catálogo da loja listado com sucesso", catalogService.listCatalog()));
    }

    @GetMapping("/{produtoId}")
    public ResponseEntity<ApiResponse<StoreProductDTO>> getProduct(@PathVariable Long produtoId) {
        StoreProductDTO product = catalogService.getProduct(produtoId);
        analyticsService.track("product_view", null, produtoId, null, null);
        return ResponseEntity.ok(ApiResponse.success("Produto da loja encontrado", product));
    }
}
