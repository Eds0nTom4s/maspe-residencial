package com.restaurante.financeiro.controller;

import com.restaurante.dto.request.UpdateTenantPaymentMethodRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantPaymentMethodResponse;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodAdminService;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/payment-methods")
@RequiredArgsConstructor
@Tag(name = "Tenant Payment Methods", description = "Configuração tenant-aware de métodos de pagamento (CASH/TPA/AppyPay)")
public class TenantPaymentMethodController {

    private final TenantGuard tenantGuard;
    private final TenantPaymentMethodAdminService adminService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantPaymentMethodResponse>>> listar() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        List<TenantPaymentMethodResponse> resp = adminService.listar().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success("Métodos de pagamento", resp));
    }

    @GetMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPaymentMethodResponse>> buscar(@PathVariable PaymentMethodCode code) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantPaymentMethod m = adminService.buscar(code);
        return ResponseEntity.ok(ApiResponse.success("Método de pagamento", toResponse(m)));
    }

    @PatchMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPaymentMethodResponse>> atualizar(
            @PathVariable PaymentMethodCode code,
            @Valid @RequestBody UpdateTenantPaymentMethodRequest request,
            HttpServletRequest http
    ) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantPaymentMethod m = adminService.atualizar(code, request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Atualizado", toResponse(m)));
    }

    @PostMapping("/{code}/activate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPaymentMethodResponse>> activate(@PathVariable PaymentMethodCode code, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantPaymentMethod m = adminService.activate(code, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Ativado", toResponse(m)));
    }

    @PostMapping("/{code}/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPaymentMethodResponse>> deactivate(@PathVariable PaymentMethodCode code, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantPaymentMethod m = adminService.deactivate(code, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Desativado", toResponse(m)));
    }

    private TenantPaymentMethodResponse toResponse(TenantPaymentMethod m) {
        TenantPaymentMethodResponse resp = new TenantPaymentMethodResponse();
        resp.setCode(m.getCode());
        resp.setDisplayName(m.getDisplayName());
        resp.setDescription(m.getDescription());
        resp.setStatus(m.getStatus());
        resp.setType(m.getType());
        resp.setConfirmationMode(m.getConfirmationMode());
        resp.setProvider(m.getProvider());
        resp.setEnabledForQr(m.isEnabledForQr());
        resp.setEnabledForPos(m.isEnabledForPos());
        resp.setEnabledForPedido(m.isEnabledForPedido());
        resp.setEnabledForFundoConsumo(m.isEnabledForFundoConsumo());
        resp.setRequiresOpenTurno(m.isRequiresOpenTurno());
        resp.setRequiresGateway(m.isRequiresGateway());
        resp.setRequiresManualConfirmation(m.isRequiresManualConfirmation());
        resp.setMinAmount(m.getMinAmount());
        resp.setMaxAmount(m.getMaxAmount());
        resp.setCurrency(m.getCurrency());
        resp.setSortOrder(m.getSortOrder());
        resp.setIconKey(m.getIconKey());
        resp.setMetadata(adminService.readMetadata(m.getMetadataJson()));
        resp.setCreatedAt(m.getCreatedAt());
        resp.setUpdatedAt(m.getUpdatedAt());
        return resp;
    }
}

