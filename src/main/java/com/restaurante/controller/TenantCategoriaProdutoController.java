package com.restaurante.controller;

import com.restaurante.dto.request.CategoriaProdutoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.CategoriaProdutoResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.CategoriaProdutoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/categorias-produto")
@RequiredArgsConstructor
@Tag(name = "Tenant Categorias de Produto", description = "Endpoints tenant-aware para categorias do catálogo")
public class TenantCategoriaProdutoController {

    private final TenantGuard tenantGuard;
    private final CategoriaProdutoService categoriaProdutoService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategoriaProdutoResponse>>> listarAtivas() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        return ResponseEntity.ok(ApiResponse.success("Categorias listadas", categoriaProdutoService.listarAtivasDoTenant()));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoriaProdutoResponse>> criar(@Valid @RequestBody CategoriaProdutoRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        CategoriaProdutoResponse created = categoriaProdutoService.criarTenantAware(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Categoria criada", created));
    }
}
