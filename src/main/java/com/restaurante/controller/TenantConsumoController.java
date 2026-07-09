package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.ConsumoAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tenant/consumos")
@RequiredArgsConstructor
@Tag(name = "Tenant - Consumos", description = "Bloqueio/desbloqueio/encerramento de consumo/fundo (operações presenciais)")
public class TenantConsumoController {

    private final TenantGuard tenantGuard;
    private final ConsumoAdminService consumoAdminService;

    @PostMapping("/{codigoConsumo}/bloquear")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> bloquear(@PathVariable String codigoConsumo,
                                                     @RequestBody(required = false) Map<String, Object> body,
                                                     HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR
        );
        String motivo = body != null ? (String) body.get("motivo") : null;
        consumoAdminService.bloquearFundo(codigoConsumo, motivo, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Fundo bloqueado", null));
    }

    @PostMapping("/{codigoConsumo}/desbloquear")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> desbloquear(@PathVariable String codigoConsumo,
                                                        @RequestBody(required = false) Map<String, Object> body,
                                                        HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR
        );
        String motivo = body != null ? (String) body.get("motivo") : null;
        consumoAdminService.desbloquearFundo(codigoConsumo, motivo, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Fundo desbloqueado", null));
    }

    @PostMapping("/{codigoConsumo}/encerrar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> encerrar(@PathVariable String codigoConsumo,
                                                      @RequestBody(required = false) Map<String, Object> body,
                                                      HttpServletRequest http) {
        // saldo positivo é reforçado no service; aqui permitimos OPERATOR, mas OWNER/ADMIN será exigido se saldo > 0
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR
        );
        String motivo = body != null ? (String) body.get("motivo") : null;
        consumoAdminService.encerrarSessao(codigoConsumo, motivo, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Sessão encerrada", null));
    }
}

