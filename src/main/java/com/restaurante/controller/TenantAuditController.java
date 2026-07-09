package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantAuditLogResponse;
import com.restaurante.model.entity.TenantAuditLog;
import com.restaurante.model.enums.TenantAuditAction;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantAuditLogRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - Auditoria", description = "Auditoria operacional mínima do tenant (ações administrativas)")
public class TenantAuditController {

    private final TenantGuard tenantGuard;
    private final TenantAuditLogRepository tenantAuditLogRepository;

    @GetMapping("/auditoria")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TenantAuditLogResponse>>> listar(
            @RequestParam(name = "action", required = false) TenantAuditAction action,
            @RequestParam(name = "targetUserId", required = false) Long targetUserId,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();

        Page<TenantAuditLog> page;
        if (targetUserId != null) {
            page = tenantAuditLogRepository.findByTenantIdAndTargetUserId(ctx.tenantId(), targetUserId, pageable);
        } else if (action != null) {
            page = tenantAuditLogRepository.findByTenantIdAndAction(ctx.tenantId(), action, pageable);
        } else {
            page = tenantAuditLogRepository.findByTenantId(ctx.tenantId(), pageable);
        }

        Page<TenantAuditLogResponse> mapped = page.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.success("Auditoria", mapped));
    }

    private TenantAuditLogResponse toDto(TenantAuditLog log) {
        return new TenantAuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getStatus(),
                log.getActorUserId(),
                log.getTargetUserId(),
                log.getMessage(),
                log.getMetadataJson(),
                log.getCreatedAt()
        );
    }
}

