package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleDetailResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleListItemResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundlePersistResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.persist.EvidenceBundleVerificationResponse;
import com.restaurante.financeiro.snapshot.evidence.service.TurnoEvidenceBundleService;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/operacao/turnos/{turnoId}/snapshot/evidence-bundles")
@RequiredArgsConstructor
public class TenantEvidenceBundleController {

    private final TenantGuard tenantGuard;
    private final TurnoEvidenceBundleService service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EvidenceBundlePersistResponse>> criar(@PathVariable Long turnoId, HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        EvidenceBundlePersistResponse resp = service.criarPersistido(turnoId, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence bundle persistido", resp));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<EvidenceBundleListItemResponse>>> listar(@PathVariable Long turnoId, Pageable pageable) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        return ResponseEntity.ok(ApiResponse.success("Evidence bundles", service.listar(turnoId, pageable)));
    }

    @GetMapping("/{bundleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EvidenceBundleDetailResponse>> detalhar(@PathVariable Long turnoId,
                                                                             @PathVariable Long bundleId,
                                                                             HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Evidence bundle", service.detalhar(turnoId, bundleId, ip, ua)));
    }

    @PostMapping("/{bundleId}/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EvidenceBundleVerificationResponse>> verify(@PathVariable Long turnoId,
                                                                                 @PathVariable Long bundleId,
                                                                                 HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Verificação", service.verificarPersistido(turnoId, bundleId, ip, ua)));
    }
}

