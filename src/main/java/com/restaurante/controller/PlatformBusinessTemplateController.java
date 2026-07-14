package com.restaurante.controller;

import com.restaurante.businesstemplate.BusinessTemplateService;
import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.business.LegacyProvisioningUsageService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/platform/templates", "/platform/business-templates"})
@RequiredArgsConstructor
@Tag(name = "Platform Business Templates", description = "Business templates / Modelos operacionais (PLATFORM_ADMIN)")
public class PlatformBusinessTemplateController {

    private final TenantGuard tenantGuard;
    private final BusinessTemplateService businessTemplateService;
    private final LegacyProvisioningUsageService legacyUsage;

    @PostMapping("/{templateCode}/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessTemplatePreviewResponse>> preview(
            @PathVariable String templateCode,
            @Valid @RequestBody BusinessTemplateProvisionRequest request,
            HttpServletRequest http
    ) {
        tenantGuard.assertPlatformAdmin();
        legacyUsage.record(http.getRequestURI(), http);
        BusinessTemplatePreviewResponse resp = businessTemplateService.preview(templateCode, request);
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", "Thu, 31 Dec 2026 23:59:59 GMT")
                .header("Link", "</platform/business-accounts/{accountId}/businesses/preview>; rel=successor-version")
                .body(ApiResponse.success("Preview de BusinessTemplate legado", resp));
    }

    @PostMapping("/{templateCode}/provision")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessTemplateProvisionResponse>> provision(
            @PathVariable String templateCode,
            @Valid @RequestBody BusinessTemplateProvisionRequest request,
            HttpServletRequest http
    ) {
        tenantGuard.assertPlatformAdmin();
        legacyUsage.record(http.getRequestURI(), http);
        BusinessTemplateProvisionResponse resp = businessTemplateService.provision(templateCode, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Deprecation", "true")
                .header("Sunset", "Thu, 31 Dec 2026 23:59:59 GMT")
                .header("Link", "</platform/business-accounts/{accountId}/businesses/provision>; rel=successor-version")
                .body(ApiResponse.success("BusinessTemplate legado provisionado", resp));
    }
}
