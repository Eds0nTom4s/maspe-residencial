package com.restaurante.controller;

import com.restaurante.dto.business.BusinessProvisioningContracts.AccountActivationRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.CanonicalAccountCreateRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.ManagerCommandRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.ReplaceOwnerRequest;
import com.restaurante.dto.request.BusinessAccountEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountLimitsUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberCreateRequest;
import com.restaurante.dto.request.BusinessAccountMemberEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberRoleUpdateRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.BusinessAccountBillingResponse;
import com.restaurante.dto.response.BusinessAccountLimitsResponse;
import com.restaurante.dto.response.BusinessAccountMemberResponse;
import com.restaurante.dto.response.BusinessAccountResponse;
import com.restaurante.dto.response.BusinessAccountSummaryResponse;
import com.restaurante.dto.response.PlatformTenantResponse;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.service.BusinessAccountService;
import com.restaurante.service.business.BusinessAccountGovernanceService;
import com.restaurante.service.business.LegacyProvisioningUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/business-accounts")
@RequiredArgsConstructor
@Tag(name = "Platform Business Accounts", description = "Gestao administrativa de contas empresariais (PLATFORM_ADMIN)")
public class PlatformBusinessAccountController {

    private final BusinessAccountService businessAccountService;
    private final BusinessAccountGovernanceService governanceService;
    private final LegacyProvisioningUsageService legacyUsage;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<BusinessAccountSummaryResponse>>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) BusinessAccountEstado estado,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long responsavelUserId,
            @RequestParam(required = false) Boolean hasTenants
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 500)));
        return ResponseEntity.ok(ApiResponse.success(
                "BusinessAccounts",
                businessAccountService.listar(pageable, estado, search, responsavelUserId, hasTenants)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("BusinessAccount", businessAccountService.buscarPorId(id)));
    }

    @GetMapping("/{id}/tenants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PlatformTenantResponse>>> tenants(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tenants da BusinessAccount", businessAccountService.listarTenants(id)));
    }

    @GetMapping("/{id}/limits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountLimitsResponse>> limits(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Limites da BusinessAccount", businessAccountService.buscarLimites(id)));
    }

    @GetMapping("/{id}/billing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountBillingResponse>> billing(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Billing da BusinessAccount", businessAccountService.buscarBilling(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> criar(
            @Valid @RequestBody CanonicalAccountCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("BusinessAccount criada em RASCUNHO",
                        governanceService.create(request, idempotencyKey, httpRequest)));
    }

    @PostMapping("/{id}/owner/replace")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> replaceOwner(
            @PathVariable Long id,
            @Valid @RequestBody ReplaceOwnerRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success("Responsável principal substituído",
                governanceService.replaceOwner(id, request, idempotencyKey, httpRequest)));
    }

    @PostMapping("/{id}/managers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountMemberResponse>> upsertManager(
            @PathVariable Long id,
            @Valid @RequestBody ManagerCommandRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success("Gestor da Conta Empresarial actualizado",
                governanceService.upsertManager(id, request, idempotencyKey, httpRequest)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> activate(
            @PathVariable Long id,
            @Valid @RequestBody AccountActivationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success("Conta Empresarial activada",
                governanceService.activate(id, request, idempotencyKey, httpRequest)));
    }

    @PostMapping("/{id}/tenants/{tenantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PlatformTenantResponse>> associarTenant(
            @PathVariable Long id,
            @PathVariable Long tenantId,
            HttpServletRequest httpRequest
    ) {
        legacyUsage.record("POST /platform/business-accounts/{id}/tenants/{tenantId}", httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant associado a BusinessAccount", businessAccountService.associarTenant(id, tenantId)));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    @Deprecated
    @Operation(summary = "Adicionar membro pelo contrato legacy", deprecated = true)
    public ResponseEntity<ApiResponse<BusinessAccountMemberResponse>> adicionarMembro(
            @PathVariable Long id,
            @Valid @RequestBody BusinessAccountMemberCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        legacyUsage.record("POST /platform/business-accounts/{id}/members", httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Membro da BusinessAccount atualizado", businessAccountService.adicionarMembro(id, request)));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> atualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody BusinessAccountEstadoUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Estado da BusinessAccount atualizado", businessAccountService.atualizarEstado(id, request)));
    }

    @PatchMapping("/{id}/limits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessAccountLimitsResponse>> atualizarLimits(
            @PathVariable Long id,
            @Valid @RequestBody BusinessAccountLimitsUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Limites da BusinessAccount atualizados", businessAccountService.atualizarLimites(id, request)));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<BusinessAccountMemberResponse>>> listarMembros(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Membros da BusinessAccount", businessAccountService.listarMembros(id)));
    }

    @PatchMapping("/{id}/members/{memberId}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    @Deprecated
    @Operation(summary = "Alterar estado de membro pelo contrato legacy", deprecated = true)
    public ResponseEntity<ApiResponse<BusinessAccountMemberResponse>> atualizarEstadoMembro(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody BusinessAccountMemberEstadoUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        legacyUsage.record("PATCH /platform/business-accounts/{id}/members/{memberId}/estado", httpRequest);
        return ResponseEntity.ok(ApiResponse.success(
                "Estado do membro da BusinessAccount atualizado",
                businessAccountService.atualizarEstadoMembro(id, memberId, request)
        ));
    }

    @PatchMapping("/{id}/members/{memberId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Deprecated
    @Operation(summary = "Alterar role de membro pelo contrato legacy", deprecated = true)
    public ResponseEntity<ApiResponse<BusinessAccountMemberResponse>> atualizarRoleMembro(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody BusinessAccountMemberRoleUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        legacyUsage.record("PATCH /platform/business-accounts/{id}/members/{memberId}/role", httpRequest);
        return ResponseEntity.ok(ApiResponse.success(
                "Role do membro da BusinessAccount atualizado",
                businessAccountService.atualizarRoleMembro(id, memberId, request)
        ));
    }

    @DeleteMapping("/{id}/tenants/{tenantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> desassociarTenant(
            @PathVariable Long id,
            @PathVariable Long tenantId,
            HttpServletRequest httpRequest
    ) {
        legacyUsage.record("DELETE /platform/business-accounts/{id}/tenants/{tenantId}", httpRequest);
        businessAccountService.desassociarTenant(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Tenant desassociado da BusinessAccount", null));
    }
}
