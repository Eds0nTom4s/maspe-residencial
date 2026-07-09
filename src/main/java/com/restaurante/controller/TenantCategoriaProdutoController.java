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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return ResponseEntity.ok(ApiResponse.success("Categorias listadas", categoriaProdutoService.listarTodasDoTenant()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoriaProdutoResponse>> buscar(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        return ResponseEntity.ok(ApiResponse.success("Categoria encontrada", categoriaProdutoService.buscarResponsePorIdDoTenant(id)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoriaProdutoResponse>> criar(@Valid @RequestBody CategoriaProdutoRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        CategoriaProdutoResponse created = categoriaProdutoService.criarTenantAware(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Categoria criada", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoriaProdutoResponse>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaProdutoRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Categoria atualizada", categoriaProdutoService.atualizarTenantAware(id, request)));
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoriaProdutoResponse>> ativar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean ativo) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Estado da categoria atualizado", categoriaProdutoService.alterarAtivoTenantAware(id, ativo)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        categoriaProdutoService.desativarTenantAware(id);
        return ResponseEntity.ok(ApiResponse.success("Categoria removida logicamente", null));
    }
}
