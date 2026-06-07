package com.restaurante.controller;

import com.restaurante.dto.request.DespublicarCardapioRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantCardapioStatusResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantCardapioConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/cardapio")
@RequiredArgsConstructor
public class TenantCardapioController {

    private final TenantGuard tenantGuard;
    private final TenantCardapioConfigService tenantCardapioConfigService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantCardapioStatusResponse>> status() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Status do cardápio", tenantCardapioConfigService.statusForCurrentTenant()));
    }

    @PostMapping("/publicar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantCardapioStatusResponse>> publicar() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Cardápio publicado", tenantCardapioConfigService.publicarCurrentTenant()));
    }

    @PostMapping("/despublicar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantCardapioStatusResponse>> despublicar(@Valid @RequestBody(required = false) DespublicarCardapioRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        String motivo = request != null ? request.getMotivo() : null;
        return ResponseEntity.ok(ApiResponse.success("Cardápio despublicado", tenantCardapioConfigService.despublicarCurrentTenant(motivo)));
    }
}
