package com.restaurante.controller;

import com.restaurante.dto.request.AtualizarLimitesCardapioRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PlatformCardapioLimitsResponse;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantCardapioConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/tenants/{tenantId}/limites-cardapio")
@RequiredArgsConstructor
public class PlatformTenantCardapioLimitsController {

    private final TenantGuard tenantGuard;
    private final TenantCardapioConfigService tenantCardapioConfigService;

    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PlatformCardapioLimitsResponse>> atualizar(
            @PathVariable Long tenantId,
            @Valid @RequestBody AtualizarLimitesCardapioRequest request
    ) {
        tenantGuard.assertPlatformAdmin();
        PlatformCardapioLimitsResponse response = tenantCardapioConfigService.alterarLimitesPlatform(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Limites de cardápio atualizados", response));
    }
}
