package com.restaurante.controller;

import com.restaurante.dto.request.TenantSessaoConsumoConfigRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantSessaoConsumoConfigResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantSessaoConsumoConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/sessao-consumo/config")
@RequiredArgsConstructor
@Tag(name = "Tenant - Sessão de Consumo", description = "Política tenant de sessão de consumo")
public class TenantSessaoConsumoConfigController {

    private final TenantGuard tenantGuard;
    private final TenantSessaoConsumoConfigService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantSessaoConsumoConfigResponse>> obter() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Configuração de sessão de consumo", service.obterDoTenantAtual()));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantSessaoConsumoConfigResponse>> atualizar(@Valid @RequestBody TenantSessaoConsumoConfigRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Configuração de sessão de consumo atualizada", service.atualizarDoTenantAtual(request)));
    }
}
