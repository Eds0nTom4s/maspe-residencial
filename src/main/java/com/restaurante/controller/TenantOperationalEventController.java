package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.OperationalEventLogResponse;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/tenant/operacional")
@RequiredArgsConstructor
@Tag(name = "Tenant - Operacional", description = "Event log operacional tenant-aware (Pedido/SubPedido)")
public class TenantOperationalEventController {

    private final TenantGuard tenantGuard;
    private final OperationalEventLogRepository operationalEventLogRepository;

    @GetMapping("/eventos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<OperationalEventLogResponse>>> listar(
            @RequestParam(required = false) Long pedidoId,
            @RequestParam(required = false) Long subPedidoId,
            @RequestParam(required = false) OperationalEventType eventType,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR
        );
        TenantContext ctx = tenantGuard.requireContext();
        Page<OperationalEventLog> page = operationalEventLogRepository.searchByTenantAndFilters(
                ctx.tenantId(), pedidoId, subPedidoId, eventType, actorUserId, deviceId, de, ate, pageable
        );
        Page<OperationalEventLogResponse> mapped = page.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.success("Eventos operacionais", mapped));
    }

    private OperationalEventLogResponse toDto(OperationalEventLog e) {
        return new OperationalEventLogResponse(
                e.getId(),
                e.getEventType(),
                e.getEntityType(),
                e.getEntityId(),
                e.getPedido() != null ? e.getPedido().getId() : null,
                e.getSubPedido() != null ? e.getSubPedido().getId() : null,
                e.getStatusAnterior(),
                e.getStatusNovo(),
                e.getActorType(),
                e.getActorUser() != null ? e.getActorUser().getId() : null,
                e.getDispositivo() != null ? e.getDispositivo().getId() : null,
                e.getOrigem(),
                e.getMotivo(),
                e.getCreatedAt()
        );
    }
}

