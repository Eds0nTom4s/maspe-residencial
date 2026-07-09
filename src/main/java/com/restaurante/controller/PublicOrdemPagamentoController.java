package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.OrdemPagamentoStatusResponse;
import com.restaurante.service.ConsumoPublicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/ordens-pagamento")
@RequiredArgsConstructor
@Tag(name = "Ordem de Pagamento - Público", description = "Consulta pública de status de ordens de pagamento manual")
public class PublicOrdemPagamentoController {

    private final ConsumoPublicService consumoPublicService;

    @GetMapping("/{token}/status")
    @Operation(summary = "Consultar status público de ordem de pagamento manual por token")
    public ResponseEntity<ApiResponse<OrdemPagamentoStatusResponse>> status(@PathVariable String token) {
        OrdemPagamentoStatusResponse resp = consumoPublicService.statusOrdemPorToken(token);
        return ResponseEntity.ok(ApiResponse.success("Status", resp));
    }
}

