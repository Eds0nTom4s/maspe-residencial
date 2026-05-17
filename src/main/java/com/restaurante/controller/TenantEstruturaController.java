package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantInstituicaoResponse;
import com.restaurante.dto.response.TenantMesaResponse;
import com.restaurante.dto.response.TenantUnidadeAtendimentoResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.tenantadmin.TenantAdminEstruturaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Estrutura", description = "Instituições, unidades e mesas do tenant atual")
public class TenantEstruturaController {

    private final TenantGuard tenantGuard;
    private final TenantAdminEstruturaService estruturaService;

    @GetMapping("/instituicoes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantInstituicaoResponse>>> listarInstituicoes() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Instituições", estruturaService.listarInstituicoes()));
    }

    @GetMapping("/instituicoes/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantInstituicaoResponse>> buscarInstituicao(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Instituição", estruturaService.buscarInstituicao(id)));
    }

    @GetMapping("/unidades")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantUnidadeAtendimentoResponse>>> listarUnidades(
            @RequestParam(required = false) Long instituicaoId,
            @RequestParam(required = false) Boolean ativa
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Unidades", estruturaService.listarUnidades(instituicaoId, ativa)));
    }

    @GetMapping("/unidades/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantUnidadeAtendimentoResponse>> buscarUnidade(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Unidade", estruturaService.buscarUnidade(id)));
    }

    @GetMapping("/mesas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantMesaResponse>>> listarMesas(
            @RequestParam(required = false) Long instituicaoId,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) Boolean ativa
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Mesas", estruturaService.listarMesas(instituicaoId, unidadeAtendimentoId, ativa)));
    }

    @GetMapping("/mesas/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantMesaResponse>> buscarMesa(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_FINANCE
        );
        return ResponseEntity.ok(ApiResponse.success("Mesa", estruturaService.buscarMesa(id)));
    }
}
