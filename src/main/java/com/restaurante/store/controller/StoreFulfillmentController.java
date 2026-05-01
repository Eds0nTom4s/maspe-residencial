package com.restaurante.store.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.store.dto.StoreSeparacaoResponse;
import com.restaurante.store.service.StoreFulfillmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/store/separacao")
public class StoreFulfillmentController {

    private final StoreFulfillmentService fulfillmentService;

    public StoreFulfillmentController(StoreFulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN','ATENDENTE')")
    public ResponseEntity<ApiResponse<List<StoreSeparacaoResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Ordens em separação listadas", fulfillmentService.listPendingFulfillment()));
    }

    @PutMapping("/{id}/processar")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN','ATENDENTE')")
    public ResponseEntity<ApiResponse<StoreSeparacaoResponse>> process(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Separação iniciada", fulfillmentService.process(id)));
    }

    @PutMapping("/{id}/entregar")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN','ATENDENTE')")
    public ResponseEntity<ApiResponse<StoreSeparacaoResponse>> deliver(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Ordem entregue", fulfillmentService.deliver(id)));
    }
}
