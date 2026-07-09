package com.restaurante.controller;

import com.restaurante.businesstemplate.BusinessTemplateService;
import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.security.tenant.TenantGuard;
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

    @PostMapping("/{templateCode}/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessTemplatePreviewResponse>> preview(
            @PathVariable String templateCode,
            @Valid @RequestBody BusinessTemplateProvisionRequest request
    ) {
        tenantGuard.assertPlatformAdmin();
        BusinessTemplatePreviewResponse resp = businessTemplateService.preview(templateCode, request);
        return ResponseEntity.ok(ApiResponse.success("Preview de BusinessTemplate", resp));
    }

    @PostMapping("/{templateCode}/provision")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BusinessTemplateProvisionResponse>> provision(
            @PathVariable String templateCode,
            @Valid @RequestBody BusinessTemplateProvisionRequest request
    ) {
        tenantGuard.assertPlatformAdmin();
        BusinessTemplateProvisionResponse resp = businessTemplateService.provision(templateCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("BusinessTemplate provisionado", resp));
    }
}

