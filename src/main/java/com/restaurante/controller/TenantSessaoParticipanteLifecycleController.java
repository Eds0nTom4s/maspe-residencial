package com.restaurante.controller;

import com.restaurante.consumo.participante.service.SessaoParticipanteExtendedListService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 41.4 — Health check do job de expiração de participantes.
 * Acessível por TENANT_OWNER, TENANT_ADMIN, TENANT_FINANCE.
 * Bloqueado para TENANT_OPERATOR, TENANT_KITCHEN, TENANT_CASHIER.
 */
@RestController
@RequestMapping("/tenant/sessao-participantes/lifecycle")
@RequiredArgsConstructor
@Tag(name = "Tenant - Lifecycle Health (41.4)", description = "Health check e observabilidade do job de expiração de participantes")
public class TenantSessaoParticipanteLifecycleController {

    private final SessaoParticipanteExtendedListService extendedListService;
    private final TenantGuard tenantGuard;

    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Health check do job de expiração de participantes pendentes")
    public ResponseEntity<ApiResponse<SessaoParticipanteExtendedListService.JobHealthView>> health(
            HttpServletRequest http
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE
        );

        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;

        // Health check é global — não requer Tenant entity, passa null
        var health = extendedListService.getJobHealth(null, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Job health", health));
    }
}
