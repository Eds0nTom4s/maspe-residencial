package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PlatformTenantResponse;
import com.restaurante.service.PlatformTenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
@Tag(name = "Platform Tenants", description = "Gestão administrativa de tenants da CONSUMA Platform")
public class PlatformTenantAccessController {

    private final PlatformTenantAccessService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Listar todos os tenants da plataforma",
            description = "Retorna todos os tenants visíveis ao Platform Admin. Não depende de vínculo TenantUser.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<List<PlatformTenantResponse>>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size,
            HttpServletRequest request
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 500));
        Pageable pageable = PageRequest.of(safePage, safeSize);
        List<PlatformTenantResponse> tenants = service.listarTenants(pageable, request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Tenants da plataforma", tenants));
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Detalhar tenant da plataforma",
            description = "Retorna detalhe administrativo de um tenant para Platform Admin.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<PlatformTenantResponse>> detalhe(
            @PathVariable Long tenantId,
            HttpServletRequest request
    ) {
        PlatformTenantResponse tenant = service.detalhe(tenantId, request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Tenant da plataforma", tenant));
    }
}
