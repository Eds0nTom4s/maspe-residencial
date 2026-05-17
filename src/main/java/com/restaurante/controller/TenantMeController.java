package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantMeResponse;
import com.restaurante.service.tenantadmin.TenantAdminContextService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Contexto", description = "Endpoints mínimos para painel tenant-admin (piloto)")
public class TenantMeController {

    private final TenantAdminContextService contextService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantMeResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success("Tenant context", contextService.me()));
    }
}

