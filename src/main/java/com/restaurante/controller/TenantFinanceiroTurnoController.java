package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.financeiro.caixa.dto.RelatorioCaixaTurnoResponse;
import com.restaurante.financeiro.caixa.service.RelatorioCaixaTurnoService;
import com.restaurante.financeiro.snapshot.dto.SnapshotFinanceiroExportResponse;
import com.restaurante.financeiro.snapshot.service.SnapshotFinanceiroExportService;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/tenant/financeiro/turnos")
@RequiredArgsConstructor
@Tag(name = "Tenant - Financeiro (Caixa por Turno)", description = "Relatório operacional de caixa por turno (manual + AppyPay)")
public class TenantFinanceiroTurnoController {

    private final TenantGuard tenantGuard;
    private final RelatorioCaixaTurnoService relatorioCaixaTurnoService;
    private final SnapshotFinanceiroExportService snapshotFinanceiroExportService;

    @GetMapping("/{turnoId}/relatorio-caixa")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RelatorioCaixaTurnoResponse>> relatorio(@PathVariable Long turnoId) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_OPERATOR
        );
        TenantContext ctx = TenantContextHolder.require();
        RelatorioCaixaTurnoResponse resp = relatorioCaixaTurnoService.gerarRelatorio(ctx.tenantId(), turnoId);
        return ResponseEntity.ok(ApiResponse.success("Relatório de caixa do turno", resp));
    }

    @GetMapping("/{turnoId}/snapshot/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SnapshotFinanceiroExportResponse>> exportSnapshot(@PathVariable Long turnoId,
                                                                                        HttpServletRequest request) {
        // Export com hash de integridade: restringir mais que relatório.
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        SnapshotFinanceiroExportResponse resp = snapshotFinanceiroExportService.exportar(
                turnoId,
                request != null ? request.getRemoteAddr() : null,
                request != null ? request.getHeader("User-Agent") : null
        );
        return ResponseEntity.ok(ApiResponse.success("Snapshot financeiro exportado", resp));
    }
}
