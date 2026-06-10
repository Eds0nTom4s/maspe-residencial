package com.restaurante.controller;

import com.restaurante.dto.request.BusinessAccountCreateRequest;
import com.restaurante.dto.request.BusinessAccountEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberCreateRequest;
import com.restaurante.dto.request.BusinessAccountMemberEstadoUpdateRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.BusinessAccountMemberResponse;
import com.restaurante.dto.response.BusinessAccountResponse;
import com.restaurante.dto.response.BusinessAccountSummaryResponse;
import com.restaurante.dto.response.PlatformTenantResponse;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.service.BusinessAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/business-accounts")
@RequiredArgsConstructor
@Tag(name = "Platform Business Accounts", description = "Gestão administrativa de contas empresariais (PLATFORM_ADMIN)")
public class PlatformBusinessAccountController {

    private final BusinessAccountService businessAccountService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BusinessAccountSummaryResponse>>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) BusinessAccountEstado estado,
            @RequestParam(required = false) String search
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 500)));
        return ResponseEntity.ok(ApiResponse.success(
                "BusinessAccounts",
                businessAccountService.listar(pageable, estado, search)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("BusinessAccount", businessAccountService.buscarPorId(id)));
    }

    @GetMapping("/{id}/tenants")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PlatformTenantResponse>>> tenants(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tenants da BusinessAccount", businessAccountService.listarTenants(id)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> criar(@Valid @RequestBody BusinessAccountCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("BusinessAccount criada", businessAccountService.criar(request)));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessAccountResponse>> atualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody BusinessAccountEstadoUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Estado da BusinessAccount atualizado", businessAccountService.atualizarEstado(id, request)));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessAccountMemberResponse>> adicionarMembro(
            @PathVariable Long id,
            @Valid @RequestBody BusinessAccountMemberCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Membro da BusinessAccount atualizado", businessAccountService.adicionarMembro(id, request)));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BusinessAccountMemberResponse>>> listarMembros(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Membros da BusinessAccount", businessAccountService.listarMembros(id)));
    }

    @PatchMapping("/{id}/members/{memberId}/estado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessAccountMemberResponse>> atualizarEstadoMembro(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody BusinessAccountMemberEstadoUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Estado do membro da BusinessAccount atualizado",
                businessAccountService.atualizarEstadoMembro(id, memberId, request)
        ));
    }
}
