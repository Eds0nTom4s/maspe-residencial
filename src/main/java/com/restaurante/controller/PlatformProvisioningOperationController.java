package com.restaurante.controller;

import com.restaurante.dto.business.BusinessProvisioningContracts.ProvisioningOperationResponse;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.service.business.BusinessProvisioningService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/provisioning-operations")
@RequiredArgsConstructor
public class PlatformProvisioningOperationController {
    private final BusinessProvisioningService service;

    @GetMapping("/{operationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProvisioningOperationResponse>> byOperationId(@PathVariable String operationId) {
        return ResponseEntity.ok(ApiResponse.success("Operação de provisionamento", service.operation(operationId)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProvisioningOperationResponse>> byIdempotencyKey(
            @RequestParam Long businessAccountId,
            @RequestParam String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.success("Operação de provisionamento",
                service.operation(businessAccountId, idempotencyKey)));
    }

    @PostMapping("/{operationId}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProvisioningOperationResponse>> resume(
            @PathVariable String operationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success("Operação de provisionamento retomada",
                service.resume(operationId, idempotencyKey, httpRequest)));
    }
}
