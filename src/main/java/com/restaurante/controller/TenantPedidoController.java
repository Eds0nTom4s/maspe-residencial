package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.request.AtualizarStatusPedidoRequest;
import com.restaurante.dto.request.ConfirmarPedidoPaymentOrderRequest;
import com.restaurante.dto.request.RejeitarPedidoRequest;
import com.restaurante.dto.response.PaymentOrderResponse;
import com.restaurante.dto.response.TenantPedidoDetalheResponse;
import com.restaurante.dto.response.TenantPedidoResumoResponse;
import com.restaurante.financeiro.service.OrdemPagamentoService;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.tenantadmin.TenantAdminPedidoService;
import com.restaurante.service.operacional.PedidoStatusTransitionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Pedidos", description = "Listagem e detalhe de pedidos do tenant atual")
public class TenantPedidoController {

    private final TenantGuard tenantGuard;
    private final TenantAdminPedidoService pedidoService;
    private final PedidoStatusTransitionService pedidoStatusTransitionService;
    private final OrdemPagamentoService ordemPagamentoService;

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
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        Page<TenantPedidoResumoResponse> page = pedidoService.listarPedidos(
                statusOperacional, statusFinanceiro, instituicaoId, unidadeAtendimentoId, mesaId, de, ate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Pedidos", page));
    }

    @GetMapping("/pedidos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPedidoDetalheResponse>> detalhe(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Pedido", pedidoService.buscarDetalhe(id)));
    }

    @PatchMapping("/pedidos/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPedidoDetalheResponse>> atualizarStatus(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarStatusPedidoRequest request,
            jakarta.servlet.http.HttpServletRequest http
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantPedidoDetalheResponse resp = pedidoStatusTransitionService.atualizarStatusPedido(id, request.getStatus(), request.getMotivo(), ip, ua);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Status atualizado", resp));
    }

    @PatchMapping("/pedidos/{id}/aceitar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPedidoDetalheResponse>> aceitar(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest http
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantPedidoDetalheResponse resp = pedidoStatusTransitionService.aceitarPedido(id, ip, ua);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Pedido aceite", resp));
    }

    @PatchMapping("/pedidos/{id}/rejeitar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPedidoDetalheResponse>> rejeitar(
            @PathVariable Long id,
            @Valid @RequestBody RejeitarPedidoRequest request,
            jakarta.servlet.http.HttpServletRequest http
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantPedidoDetalheResponse resp = pedidoStatusTransitionService.rejeitarPedido(id, request.getMotivo(), ip, ua);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Pedido rejeitado", resp));
    }

    @PatchMapping("/pedidos/{id}/payment-order/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> confirmarPaymentOrder(
            @PathVariable Long id,
            @RequestBody(required = false) ConfirmarPedidoPaymentOrderRequest request,
            jakarta.servlet.http.HttpServletRequest http
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        TenantContext ctx = tenantGuard.requireContext();
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentOrderResponse resp = ordemPagamentoService.confirmarOrdemPedidoPorOperador(
                ctx.tenantId(),
                id,
                ctx.userId(),
                resolvePaymentOrigem(ctx),
                request,
                ip,
                ua
        );
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Ordem de pagamento confirmada", resp));
    }

    private OperationalOrigem resolvePaymentOrigem(TenantContext ctx) {
        if (ctx == null) {
            return OperationalOrigem.SYSTEM;
        }
        if (ctx.platformAdmin()) {
            return OperationalOrigem.TENANT_ADMIN;
        }
        Set<String> roles = ctx.roles() != null ? ctx.roles() : Collections.emptySet();
        if (roles.contains(TenantUserRole.TENANT_OWNER.name())) return OperationalOrigem.TENANT_OWNER;
        if (roles.contains(TenantUserRole.TENANT_ADMIN.name())) return OperationalOrigem.TENANT_ADMIN;
        if (roles.contains(TenantUserRole.TENANT_FINANCE.name())) return OperationalOrigem.TENANT_FINANCE;
        if (roles.contains(TenantUserRole.TENANT_CASHIER.name())) return OperationalOrigem.TENANT_CASHIER;
        if (roles.contains(TenantUserRole.TENANT_OPERATOR.name())) return OperationalOrigem.TENANT_OPERATOR;
        return OperationalOrigem.SYSTEM;
    }
}
