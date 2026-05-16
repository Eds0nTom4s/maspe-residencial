package com.restaurante.controller;

import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.ProvisioningTemplate;
import com.restaurante.repository.ProvisioningTemplateRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantProvisioningService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform Provisioning", description = "Provisionamento manual administrado (PLATFORM_ADMIN)")
public class PlatformTenantProvisioningController {

    private final TenantGuard tenantGuard;
    private final TenantProvisioningService provisioningService;
    private final ProvisioningTemplateRepository templateRepository;

    @PostMapping("/provisionar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProvisionarTenantResponse>> provisionar(@Valid @RequestBody ProvisionarTenantRequest request) {
        tenantGuard.assertPlatformAdmin();
        ProvisionarTenantResponse resp = provisioningService.provisionar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tenant provisionado", resp));
    }

    @GetMapping("/templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProvisioningTemplate>>> listarTemplates() {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Templates", templateRepository.findByAtivoTrue()));
    }
}

