package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantOperationalModulesResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantOperationalModulesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/modulos-operacionais")
@RequiredArgsConstructor
@Tag(name = "Tenant - Módulos Operacionais", description = "Consulta dos módulos operacionais habilitados para o tenant")
public class TenantOperationalModulesController {

    private final TenantGuard tenantGuard;
    private final TenantOperationalModulesService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantOperationalModulesResponse>> obter() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Módulos operacionais", service.obterDoTenantAtual()));
    }
}
