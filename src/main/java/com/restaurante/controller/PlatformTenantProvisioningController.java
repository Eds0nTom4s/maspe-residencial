package com.restaurante.controller;

import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ProvisionarTenantPreviewResponse;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.exception.ProvisioningErrorResponse;
import com.restaurante.exception.ProvisioningException;
import com.restaurante.model.entity.ProvisioningTemplate;
import com.restaurante.repository.ProvisioningTemplateRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.service.TenantProvisioningPreviewService;
import com.restaurante.service.business.LegacyProvisioningUsageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform Provisioning", description = "Provisionamento manual administrado (PLATFORM_ADMIN)")
public class PlatformTenantProvisioningController {

    private final TenantGuard tenantGuard;
    private final TenantProvisioningService provisioningService;
    private final TenantProvisioningPreviewService provisioningPreviewService;
    private final ProvisioningTemplateRepository templateRepository;
    private final LegacyProvisioningUsageService legacyUsage;

    @PostMapping("/provisionar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> provisionar(@Valid @RequestBody ProvisionarTenantRequest request, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        legacyUsage.record(http.getRequestURI(), http);
        try {
            ProvisionarTenantResponse resp = provisioningService.provisionar(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("Deprecation", "true")
                    .header("Sunset", "Thu, 31 Dec 2026 23:59:59 GMT")
                    .header("Link", "</platform/business-accounts/{accountId}/businesses/provision>; rel=successor-version")
                    .body(ApiResponse.success("Tenant provisionado por adapter legado", resp));
        } catch (ProvisioningException ex) {
            return provisioningError(ex, http);
        }
    }

    @PostMapping("/provisionar/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> preview(@Valid @RequestBody ProvisionarTenantRequest request, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        legacyUsage.record(http.getRequestURI(), http);
        try {
            ProvisionarTenantPreviewResponse resp = provisioningPreviewService.preview(request);
            return ResponseEntity.ok()
                    .header("Deprecation", "true")
                    .header("Sunset", "Thu, 31 Dec 2026 23:59:59 GMT")
                    .header("Link", "</platform/business-accounts/{accountId}/businesses/preview>; rel=successor-version")
                    .body(ApiResponse.success("Preview de provisionamento legado", resp));
        } catch (ProvisioningException ex) {
            return provisioningError(ex, http);
        }
    }

    @GetMapping("/templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProvisioningTemplate>>> listarTemplates() {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Templates", templateRepository.findByAtivoTrue()));
    }

    private ResponseEntity<ProvisioningErrorResponse> provisioningError(ProvisioningException ex, HttpServletRequest http) {
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.BAD_REQUEST;
        String path = http != null ? http.getRequestURI() : null;
        String correlationId = UUID.randomUUID().toString();
        ProvisioningErrorResponse resp = new ProvisioningErrorResponse(
                LocalDateTime.now(),
                status.value(),
                "Provisioning error",
                ex.getMessage(),
                ex.getCode(),
                ex.getField(),
                ex.getDetail(),
                ex.getRecommendedAction(),
                path,
                correlationId,
                ex.getExtra()
        );
        return ResponseEntity.status(status).body(resp);
    }
}
