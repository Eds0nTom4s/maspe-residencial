package com.restaurante.financeiro.controller;

import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PaymentMethodPolicyResponse;
import com.restaurante.financeiro.paymentmethod.entity.DevicePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.service.DevicePaymentMethodPolicyAdminService;
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
@RequestMapping("/tenant/devices/{deviceId}/payment-method-policies")
@RequiredArgsConstructor
@Tag(name = "Device Payment Method Policies", description = "Política de métodos de pagamento por device (tenant-aware)")
public class TenantDevicePaymentMethodPolicyController {

    private final TenantGuard tenantGuard;
    private final DevicePaymentMethodPolicyAdminService adminService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PaymentMethodPolicyResponse>>> list(@PathVariable Long deviceId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        List<PaymentMethodPolicyResponse> resp = adminService.listPolicies(deviceId).stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success("Políticas do device", resp));
    }

    @GetMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentMethodPolicyResponse>> get(@PathVariable Long deviceId, @PathVariable PaymentMethodCode code) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        DevicePaymentMethodPolicy p = adminService.getPolicy(deviceId, code);
        return ResponseEntity.ok(ApiResponse.success("Política", toResponse(p)));
    }

    @PutMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentMethodPolicyResponse>> upsert(
            @PathVariable Long deviceId,
            @PathVariable PaymentMethodCode code,
            @Valid @RequestBody UpdateDevicePaymentMethodPolicyRequest req,
            HttpServletRequest http
    ) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        DevicePaymentMethodPolicy saved = adminService.upsert(deviceId, code, req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Política atualizada", toResponse(saved)));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long deviceId, @PathVariable PaymentMethodCode code, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        adminService.remove(deviceId, code, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Política removida", null));
    }

    private PaymentMethodPolicyResponse toResponse(DevicePaymentMethodPolicy p) {
        PaymentMethodPolicyResponse r = new PaymentMethodPolicyResponse();
        r.setId(p.getId());
        r.setCode(p.getPaymentMethodCode());
        r.setStatus(p.getStatus());
        r.setInherit(p.isInheritFromUnidade());
        r.setEnabledForPos(p.getEnabledForPos());
        r.setEnabledForPedido(p.getEnabledForPedido());
        r.setEnabledForFundoConsumo(p.getEnabledForFundoConsumo());
        r.setCanConfirmManual(p.getCanConfirmManual());
        r.setCanStartGateway(p.getCanStartGateway());
        r.setMinAmount(p.getMinAmount());
        r.setMaxAmount(p.getMaxAmount());
        r.setOverrideReason(p.getOverrideReason());
        r.setMetadata(adminService.readMetadata(p.getMetadataJson()));
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}

