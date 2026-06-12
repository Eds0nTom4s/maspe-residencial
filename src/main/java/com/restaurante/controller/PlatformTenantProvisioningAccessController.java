package com.restaurante.controller;

import com.restaurante.dto.request.PlatformTenantAccessResetPasswordRequest;
import com.restaurante.dto.request.TenantProvisioningAccessRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PlatformTenantAccessResetPasswordResponse;
import com.restaurante.dto.response.PlatformTenantAccessSummaryResponse;
import com.restaurante.dto.response.TenantProvisioningAccessResponse;
import com.restaurante.service.PlatformTenantProvisioningAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform Tenant Provisioning Access", description = "Provisionamento completo de tenant com owner e acesso inicial")
public class PlatformTenantProvisioningAccessController {

    private final PlatformTenantProvisioningAccessService service;

    @PostMapping("/provision-with-access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TenantProvisioningAccessResponse>> provisionWithAccess(
            @Valid @RequestBody TenantProvisioningAccessRequest request
    ) {
        TenantProvisioningAccessResponse response = service.provisionWithAccess(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant provisionado com acesso inicial", response));
    }

    @GetMapping("/{tenantId}/access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PlatformTenantAccessSummaryResponse>> getAccessSummary(@PathVariable Long tenantId) {
        return ResponseEntity.ok(ApiResponse.success("Resumo de acesso do tenant", service.getAccessSummary(tenantId)));
    }

    @PostMapping("/{tenantId}/access/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PlatformTenantAccessResetPasswordResponse>> resetPassword(
            @PathVariable Long tenantId,
            @RequestBody(required = false) PlatformTenantAccessResetPasswordRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Senha temporaria regenerada",
                service.resetTemporaryPassword(tenantId, request)
        ));
    }
}
