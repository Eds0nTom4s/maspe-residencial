package com.restaurante.financeiro.caixa.controller;

import com.restaurante.dto.request.ConfirmarPagamentoManualPedidoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantPedidoDetalheResponse;
import com.restaurante.financeiro.caixa.dto.CaixaPedidoResponse;
import com.restaurante.financeiro.caixa.dto.CaixaResumoResponse;
import com.restaurante.financeiro.caixa.service.TenantCaixaService;
import com.restaurante.financeiro.monitoramento.dto.PagamentoResumoDTO;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.tenantadmin.TenantPedidoWorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/tenant/caixa")
@RequiredArgsConstructor
@Tag(name = "Tenant Caixa", description = "Contratos de caixa híbrido tenant-scoped")
public class TenantCaixaController {

    private final TenantGuard tenantGuard;
    private final TenantCaixaService tenantCaixaService;
    private final TenantPedidoWorkflowService tenantPedidoWorkflowService;

    @GetMapping("/pedidos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<CaixaPedidoResponse>>> listarPedidos(
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String statusFinanceiro,
            @RequestParam(required = false) String operationalStatus,
            @RequestParam(required = false) String statusOperacional,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String metodoPagamento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        assertCashierAccess();
        Page<CaixaPedidoResponse> page = tenantCaixaService.listarPedidos(
                paymentStatus,
                statusFinanceiro,
                operationalStatus,
                statusOperacional,
                paymentMethod,
                metodoPagamento,
                dateFrom,
                dateTo,
                search,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Pedidos do caixa", page));
    }

    @GetMapping("/resumo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CaixaResumoResponse>> resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo
    ) {
        assertCashierAccess();
        return ResponseEntity.ok(ApiResponse.success("Resumo do caixa", tenantCaixaService.resumo(dateFrom, dateTo)));
    }

    @GetMapping("/historico")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PagamentoResumoDTO>>> historico(
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String statusFinanceiro,
            @RequestParam(required = false) String pedidoNumero,
            @RequestParam(required = false) String externalReference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        assertCashierAccess();
        Page<PagamentoResumoDTO> page = tenantCaixaService.historico(
                paymentStatus,
                statusFinanceiro,
                pedidoNumero,
                externalReference,
                dateFrom,
                dateTo,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Histórico financeiro do caixa", page));
    }

    @PostMapping("/pedidos/{pedidoId}/pagamento/manual-confirmar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPedidoDetalheResponse>> confirmarPagamentoManual(
            @PathVariable Long pedidoId,
            @Valid @RequestBody ConfirmarPagamentoManualPedidoRequest request,
            jakarta.servlet.http.HttpServletRequest http
    ) {
        assertCashierAccess();
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantPedidoDetalheResponse resp = tenantPedidoWorkflowService.confirmarPagamentoManual(pedidoId, request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Pagamento manual confirmado", resp));
    }

    private void assertCashierAccess() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
    }
}
