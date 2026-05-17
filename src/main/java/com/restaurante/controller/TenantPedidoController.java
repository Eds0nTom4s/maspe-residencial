package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantPedidoDetalheResponse;
import com.restaurante.dto.response.TenantPedidoResumoResponse;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.service.tenantadmin.TenantAdminPedidoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Pedidos", description = "Listagem e detalhe de pedidos do tenant atual")
public class TenantPedidoController {

    private final TenantAdminPedidoService pedidoService;

    @GetMapping("/pedidos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TenantPedidoResumoResponse>>> listar(
            @RequestParam(required = false) StatusPedido statusOperacional,
            @RequestParam(required = false) StatusFinanceiroPedido statusFinanceiro,
            @RequestParam(required = false) Long instituicaoId,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) Long mesaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<TenantPedidoResumoResponse> page = pedidoService.listarPedidos(
                statusOperacional, statusFinanceiro, instituicaoId, unidadeAtendimentoId, mesaId, de, ate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Pedidos", page));
    }

    @GetMapping("/pedidos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPedidoDetalheResponse>> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Pedido", pedidoService.buscarDetalhe(id)));
    }
}

