package com.restaurante.controller;

import com.restaurante.dto.request.TenantOwnershipBackfillRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantOwnershipBackfillDiagnosticResponse;
import com.restaurante.dto.response.TenantOwnershipBackfillResponse;
import com.restaurante.service.TenantOwnershipBackfillService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform Tenant Ownership Backfill", description = "Endpoints de diagnóstico e correção de ownership para tenants legados")
public class PlatformTenantOwnershipBackfillController {

    private final TenantOwnershipBackfillService service;

    @GetMapping("/{tenantId}/ownership-diagnostic")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TenantOwnershipBackfillDiagnosticResponse>> diagnose(@PathVariable Long tenantId) {
        TenantOwnershipBackfillDiagnosticResponse diagnostic = service.diagnose(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Diagnóstico de ownership do tenant gerado", diagnostic));
    }

    @PostMapping("/{tenantId}/ownership-backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TenantOwnershipBackfillResponse>> executeBackfill(
            @PathVariable Long tenantId,
            @RequestBody(required = false) TenantOwnershipBackfillRequest request
    ) {
        TenantOwnershipBackfillRequest actualRequest = request != null ? request : new TenantOwnershipBackfillRequest();
        TenantOwnershipBackfillResponse response = service.executeBackfill(tenantId, actualRequest);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Correção de ownership executada com sucesso", response));
    }
}
