package com.restaurante.controller;

import com.restaurante.dto.request.TenantPagamentoPolicyRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantPagamentoPolicyResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantPagamentoPolicyService;
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
@RequestMapping("/tenant/configuracoes/pagamento")
@RequiredArgsConstructor
@Tag(name = "Tenant Configurações de Pagamento", description = "Política de pedido e pagamento do tenant")
public class TenantPagamentoPolicyController {

    private final TenantGuard tenantGuard;
    private final TenantPagamentoPolicyService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPagamentoPolicyResponse>> obter() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        return ResponseEntity.ok(ApiResponse.success("Política de pagamento", service.obterDoTenantAtual()));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantPagamentoPolicyResponse>> atualizar(@Valid @RequestBody TenantPagamentoPolicyRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        return ResponseEntity.ok(ApiResponse.success("Política de pagamento atualizada", service.atualizarDoTenantAtual(request)));
    }
}
