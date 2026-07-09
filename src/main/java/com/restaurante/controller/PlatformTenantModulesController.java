package com.restaurante.controller;

import com.restaurante.dto.request.PlatformTenantOperationalModulesPatchRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantOperationalModulesResponse;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantOperationalModulesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/tenants/{tenantId}/modulos-operacionais")
@RequiredArgsConstructor
@Tag(name = "Platform - Módulos Operacionais", description = "Configuração Platform dos módulos operacionais por tenant")
public class PlatformTenantModulesController {

    private final TenantGuard tenantGuard;
    private final TenantOperationalModulesService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantOperationalModulesResponse>> obter(@PathVariable Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Módulos operacionais", service.toResponse(service.obterParaTenant(tenantId))));
    }

    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantOperationalModulesResponse>> atualizar(
            @PathVariable Long tenantId,
            @RequestBody PlatformTenantOperationalModulesPatchRequest request) {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Módulos operacionais atualizados", service.atualizarPelaPlatform(tenantId, request)));
    }
}
