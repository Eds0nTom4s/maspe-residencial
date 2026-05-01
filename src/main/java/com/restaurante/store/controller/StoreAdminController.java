package com.restaurante.store.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.store.dto.StoreAdminResumoDTO;
import com.restaurante.store.dto.StoreOrderDTO;
import com.restaurante.store.service.StoreOrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/admin")
@PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
public class StoreAdminController {

    private final StoreOrderService orderService;

    public StoreAdminController(StoreOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/resumo")
    public ResponseEntity<ApiResponse<StoreAdminResumoDTO>> resumo() {
        return ResponseEntity.ok(ApiResponse.success("Resumo operacional da loja", orderService.getAdminSummary()));
    }

    @GetMapping("/ordens")
    public ResponseEntity<ApiResponse<Page<StoreOrderDTO>>> listOrders(@PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Ordens da loja listadas", orderService.listAdminOrders(pageable)));
    }
}
