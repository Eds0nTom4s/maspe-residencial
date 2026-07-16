package com.restaurante.controller;

import com.restaurante.dto.request.OnboardingRequestApproveRequest;
import com.restaurante.dto.request.OnboardingRequestCreateRequest;
import com.restaurante.dto.request.OnboardingRequestRejectRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.OnboardingRequestResponse;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.service.PlatformOnboardingRequestService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/platform/onboarding-requests")
@RequiredArgsConstructor
@Tag(name = "Platform Onboarding Requests", description = "Fila administrativa de onboarding comercial")
public class PlatformOnboardingRequestController {

    private final PlatformOnboardingRequestService onboardingRequestService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OnboardingRequestResponse>>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) OnboardingRequestStatus status,
            @RequestParam(required = false) String search
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 500)));
        return ResponseEntity.ok(ApiResponse.success(
                "Onboarding requests",
                onboardingRequestService.listar(pageable, status, search)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OnboardingRequestResponse>> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Onboarding request", onboardingRequestService.buscarPorId(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OnboardingRequestResponse>> criar(
            @Valid @RequestBody OnboardingRequestCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Onboarding request criada", onboardingRequestService.criar(request)));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OnboardingRequestResponse>> aprovar(
            @PathVariable Long id,
            @Valid @RequestBody OnboardingRequestApproveRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success("Onboarding request aprovada",
                onboardingRequestService.aprovar(id, request, idempotencyKey, httpRequest)));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OnboardingRequestResponse>> rejeitar(
            @PathVariable Long id,
            @Valid @RequestBody OnboardingRequestRejectRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Onboarding request rejeitada", onboardingRequestService.rejeitar(id, request)));
    }
}
