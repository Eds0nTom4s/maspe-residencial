package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.financeiro.caixa.dto.RelatorioCaixaTurnoResponse;
import com.restaurante.financeiro.caixa.service.RelatorioCaixaTurnoService;
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

@RestController
@RequestMapping("/tenant/financeiro/turnos")
@RequiredArgsConstructor
@Tag(name = "Tenant - Financeiro (Caixa por Turno)", description = "Relatório operacional de caixa por turno (manual + AppyPay)")
public class TenantFinanceiroTurnoController {

    private final TenantGuard tenantGuard;
    private final RelatorioCaixaTurnoService relatorioCaixaTurnoService;

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
}

