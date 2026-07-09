package com.restaurante.controller;

import com.restaurante.dto.request.AlterarTenantUsuarioRolesRequest;
import com.restaurante.dto.request.CriarTenantUsuarioRequest;
import com.restaurante.dto.request.ReativarTenantUsuarioRequest;
import com.restaurante.dto.request.SuspenderTenantUsuarioRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantUsuarioResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.tenantadmin.TenantUsuarioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Usuários", description = "Gestão mínima de usuários/memberships do tenant (TenantUser)")
public class TenantUsuarioController {

    private final TenantGuard tenantGuard;
    private final TenantUsuarioService tenantUsuarioService;

    @GetMapping("/usuarios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TenantUsuarioResponse>>> listar(Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Usuários do tenant", tenantUsuarioService.listar(pageable)));
    }

    @GetMapping("/usuarios/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantUsuarioResponse>> buscar(@PathVariable Long userId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Usuário do tenant", tenantUsuarioService.buscar(userId)));
    }

    @PostMapping("/usuarios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantUsuarioResponse>> criar(@Valid @RequestBody CriarTenantUsuarioRequest request, HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TenantUsuarioResponse resp = tenantUsuarioService.criarOuConvidar(request, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Usuário vinculado ao tenant", resp));
    }

    @PutMapping("/usuarios/{userId}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantUsuarioResponse>> alterarRoles(@PathVariable Long userId,
                                                                          @Valid @RequestBody AlterarTenantUsuarioRolesRequest request,
                                                                          HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Roles atualizadas", tenantUsuarioService.alterarRoles(userId, request, ip, ua)));
    }

    @PostMapping("/usuarios/{userId}/suspender")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantUsuarioResponse>> suspender(@PathVariable Long userId,
                                                                       @RequestBody(required = false) SuspenderTenantUsuarioRequest request,
                                                                       HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String motivo = request != null ? request.getMotivo() : null;
        return ResponseEntity.ok(ApiResponse.success("Usuário suspenso", tenantUsuarioService.suspender(userId, motivo, ip, ua)));
    }

    @PostMapping("/usuarios/{userId}/reativar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantUsuarioResponse>> reativar(@PathVariable Long userId,
                                                                      @RequestBody(required = false) ReativarTenantUsuarioRequest request,
                                                                      HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Usuário reativado", tenantUsuarioService.reativar(userId, request, ip, ua)));
    }

    @DeleteMapping("/usuarios/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable Long userId,
                                                     @RequestBody(required = false) SuspenderTenantUsuarioRequest request,
                                                     HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String motivo = request != null ? request.getMotivo() : null;
        tenantUsuarioService.remover(userId, motivo, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Usuário removido do tenant", null));
    }
}

