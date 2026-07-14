package com.restaurante.controller;

import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessActivationRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessPreviewRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessPreviewResponse;
import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessProvisionRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessReadinessResponse;
import com.restaurante.dto.business.BusinessProvisioningContracts.ProvisioningOperationResponse;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.service.business.BusinessProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/business-accounts/{accountId}/businesses")
@RequiredArgsConstructor
public class PlatformCanonicalBusinessProvisioningController {
    private final BusinessProvisioningService service;

    @PostMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessPreviewResponse>> preview(
            @PathVariable Long accountId,
            @Valid @RequestBody BusinessPreviewRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success("Preview canónico verificável",
                service.preview(accountId, request, idempotencyKey, httpRequest)));
    }

    @PostMapping("/provision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProvisioningOperationResponse>> provision(
            @PathVariable Long accountId,
            @Valid @RequestBody BusinessProvisionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        ProvisioningOperationResponse response = service.provision(accountId, request, idempotencyKey, httpRequest);
        HttpStatus status = Boolean.TRUE.equals(response.replay()) ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success("Operação de provisionamento", response));
    }

    @GetMapping("/{tenantId}/readiness")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessReadinessResponse>> readiness(
            @PathVariable Long accountId, @PathVariable Long tenantId) {
        return ResponseEntity.ok(ApiResponse.success("Readiness do negócio", service.readiness(accountId, tenantId)));
    }

    @PostMapping("/{tenantId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessReadinessResponse>> activate(
            @PathVariable Long accountId,
            @PathVariable Long tenantId,
            @Valid @RequestBody BusinessActivationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success("Negócio activado",
                service.activateBusiness(accountId, tenantId, request, idempotencyKey, httpRequest)));
    }
}
