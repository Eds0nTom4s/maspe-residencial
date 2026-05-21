package com.restaurante.financeiro.controller;

import com.restaurante.dto.request.UpdateUnidadePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PaymentMethodPolicyResponse;
import com.restaurante.financeiro.paymentmethod.entity.UnidadePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.service.UnidadePaymentMethodPolicyAdminService;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenant/unidades/{unidadeId}/payment-method-policies")
@RequiredArgsConstructor
@Tag(name = "Unidade Payment Method Policies", description = "Política de métodos de pagamento por unidade (tenant-aware)")
public class TenantUnidadePaymentMethodPolicyController {

    private final TenantGuard tenantGuard;
    private final UnidadePaymentMethodPolicyAdminService adminService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PaymentMethodPolicyResponse>>> list(@PathVariable Long unidadeId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        List<PaymentMethodPolicyResponse> resp = adminService.listPolicies(unidadeId).stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success("Políticas da unidade", resp));
    }

    @GetMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentMethodPolicyResponse>> get(@PathVariable Long unidadeId, @PathVariable PaymentMethodCode code) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        UnidadePaymentMethodPolicy p = adminService.getPolicy(unidadeId, code);
        return ResponseEntity.ok(ApiResponse.success("Política", toResponse(p)));
    }

    @PutMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentMethodPolicyResponse>> upsert(
            @PathVariable Long unidadeId,
            @PathVariable PaymentMethodCode code,
            @Valid @RequestBody UpdateUnidadePaymentMethodPolicyRequest req,
            HttpServletRequest http
    ) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        UnidadePaymentMethodPolicy saved = adminService.upsert(unidadeId, code, req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Política atualizada", toResponse(saved)));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long unidadeId, @PathVariable PaymentMethodCode code, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        adminService.remove(unidadeId, code, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Política removida", null));
    }

    private PaymentMethodPolicyResponse toResponse(UnidadePaymentMethodPolicy p) {
        PaymentMethodPolicyResponse r = new PaymentMethodPolicyResponse();
        r.setId(p.getId());
        r.setCode(p.getPaymentMethodCode());
        r.setStatus(p.getStatus());
        r.setInherit(p.isInheritFromTenant());
        r.setEnabledForQr(p.getEnabledForQr());
        r.setEnabledForPos(p.getEnabledForPos());
        r.setEnabledForPedido(p.getEnabledForPedido());
        r.setEnabledForFundoConsumo(p.getEnabledForFundoConsumo());
        r.setMinAmount(p.getMinAmount());
        r.setMaxAmount(p.getMaxAmount());
        r.setOverrideReason(p.getOverrideReason());
        r.setMetadata(adminService.readMetadata(p.getMetadataJson()));
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}

