package com.restaurante.controller;

import com.restaurante.dto.request.CriarMesaRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantInstituicaoResponse;
import com.restaurante.dto.response.TenantMesaResponse;
import com.restaurante.dto.response.TenantQrCodeResponse;
import com.restaurante.dto.response.TenantUnidadeAtendimentoResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.MesaService;
import com.restaurante.service.TenantOperationalModulesService;
import com.restaurante.service.tenantadmin.TenantAdminEstruturaService;
import com.restaurante.service.tenantadmin.TenantAdminQrService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Estrutura", description = "Instituições, unidades e mesas do tenant atual")
public class TenantEstruturaController {

    private final TenantGuard tenantGuard;
    private final TenantAdminEstruturaService estruturaService;
    private final MesaService mesaService;
    private final TenantAdminQrService qrService;
    private final TenantOperationalModulesService modulesService;

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

    @PostMapping("/mesas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantMesaResponse>> criarMesa(@Valid @RequestBody CriarMesaRequest request) {
        assertCanWrite();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Mesa criada", mesaService.criarTenantAware(request)));
    }

    @PutMapping("/mesas/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantMesaResponse>> atualizarMesa(
            @PathVariable Long id,
            @Valid @RequestBody CriarMesaRequest request) {
        assertCanWrite();
        return ResponseEntity.ok(ApiResponse.success("Mesa atualizada", mesaService.atualizarTenantAware(id, request)));
    }

    @PatchMapping("/mesas/{id}/ativar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantMesaResponse>> ativarMesa(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean ativa) {
        assertCanWrite();
        return ResponseEntity.ok(ApiResponse.success("Estado da mesa atualizado", mesaService.alterarAtivaTenantAware(id, ativa)));
    }

    @DeleteMapping("/mesas/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removerMesa(@PathVariable Long id) {
        assertCanWrite();
        mesaService.desativarTenantAware(id);
        return ResponseEntity.ok(ApiResponse.success("Mesa removida logicamente", null));
    }

    @PostMapping("/mesas/{id}/qr")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> gerarQrMesa(@PathVariable Long id) {
        assertCanWrite();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("QR da mesa gerado", qrService.gerarQrParaMesa(id)));
    }

    private void assertCanWrite() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        modulesService.assertMesasEnabled(tenantGuard.requireContext().tenantId());
    }
}
