package com.restaurante.controller;

import com.restaurante.dto.request.RegistrarDispositivoRequest;
import com.restaurante.dto.request.DefinirUnidadeProducaoDispositivoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DispositivoOperacionalResponse;
import com.restaurante.dto.response.RegistrarDispositivoResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.tenantadmin.TenantAdminDispositivoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Dispositivos", description = "Gestão de dispositivos operacionais (POS/KDS/etc.) do tenant")
public class TenantDispositivoOperacionalController {

    private final TenantGuard tenantGuard;
    private final TenantAdminDispositivoService dispositivoService;

    @PostMapping("/dispositivos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RegistrarDispositivoResponse>> registrar(@Valid @RequestBody RegistrarDispositivoRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        RegistrarDispositivoResponse resp = dispositivoService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Dispositivo registrado", resp));
    }

    @GetMapping("/dispositivos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<DispositivoOperacionalResponse>>> listar(Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Dispositivos", dispositivoService.listar(pageable)));
    }

    @GetMapping("/dispositivos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DispositivoOperacionalResponse>> buscar(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Dispositivo", dispositivoService.buscar(id)));
    }

    @PostMapping("/dispositivos/{id}/suspender")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DispositivoOperacionalResponse>> suspender(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Dispositivo suspenso", dispositivoService.suspender(id)));
    }

    @PostMapping("/dispositivos/{id}/revogar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DispositivoOperacionalResponse>> revogar(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Dispositivo revogado", dispositivoService.revogar(id)));
    }

    @PostMapping("/dispositivos/{id}/activation-code")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RegistrarDispositivoResponse>> reemitir(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        RegistrarDispositivoResponse resp = dispositivoService.reemitirActivationCode(id);
        return ResponseEntity.ok(ApiResponse.success("Activation code reemitido", resp));
    }

    @PostMapping("/dispositivos/{id}/unidade-producao")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DispositivoOperacionalResponse>> definirUnidadeProducao(
            @PathVariable Long id,
            @Valid @RequestBody DefinirUnidadeProducaoDispositivoRequest request
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success(
                "Unidade de produção definida",
                dispositivoService.definirUnidadeProducao(id, request.getUnidadeProducaoId())
        ));
    }
}
