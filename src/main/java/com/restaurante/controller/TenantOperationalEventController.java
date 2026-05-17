package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.OperationalEventLogResponse;
import com.restaurante.dto.response.OperationalEventSummaryResponse;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventRetentionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/tenant/operacional")
@RequiredArgsConstructor
@Tag(name = "Tenant - Operacional", description = "Event log operacional tenant-aware (Pedido/SubPedido)")
public class TenantOperationalEventController {

    private final TenantGuard tenantGuard;
    private final OperationalEventLogRepository operationalEventLogRepository;
    private final OperationalEventRetentionService retentionService;

    @Value("${consuma.operational-events.default-lookback-days:30}")
    private int defaultLookbackDays;

    @Value("${consuma.operational-events.max-page-size:100}")
    private int maxPageSize;

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

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveAte = ate != null ? ate : now;
        LocalDateTime effectiveDe = de != null ? de : effectiveAte.minusDays(defaultLookbackDays);

        int size = pageable != null ? pageable.getPageSize() : 20;
        int pageNumber = pageable != null ? pageable.getPageNumber() : 0;
        if (size > maxPageSize) size = maxPageSize;
        Pageable effectivePageable = PageRequest.of(pageNumber, size, pageable.getSort());

        Page<OperationalEventLog> page = operationalEventLogRepository.searchByTenantAndFilters(
                ctx.tenantId(), pedidoId, subPedidoId, eventType, actorUserId, deviceId, effectiveDe, effectiveAte, effectivePageable
        );
        Page<OperationalEventLogResponse> mapped = page.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.success("Eventos operacionais", mapped));
    }

    @GetMapping("/eventos/resumo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OperationalEventSummaryResponse>> resumo(
            @RequestParam(required = false) Long pedidoId,
            @RequestParam(required = false) Long subPedidoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR
        );
        TenantContext ctx = tenantGuard.requireContext();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveAte = ate != null ? ate : now;
        LocalDateTime effectiveDe = de != null ? de : effectiveAte.minusDays(defaultLookbackDays);

        long total;
        if (pedidoId != null || subPedidoId != null) {
            // reaproveita query principal e conta via page size 1
            total = operationalEventLogRepository.searchByTenantAndFilters(
                    ctx.tenantId(), pedidoId, subPedidoId, null, null, null, effectiveDe, effectiveAte,
                    PageRequest.of(0, 1)
            ).getTotalElements();
        } else {
            total = operationalEventLogRepository.countByTenantIdAndCreatedAtBetween(ctx.tenantId(), effectiveDe, effectiveAte);
        }

        Map<String, Long> porEventType = new LinkedHashMap<>();
        operationalEventLogRepository.countByEventType(ctx.tenantId(), effectiveDe, effectiveAte)
                .forEach(arr -> porEventType.put(String.valueOf(arr[0]), ((Number) arr[1]).longValue()));

        Map<String, Long> porOrigem = new LinkedHashMap<>();
        operationalEventLogRepository.countByOrigem(ctx.tenantId(), effectiveDe, effectiveAte)
                .forEach(arr -> porOrigem.put(String.valueOf(arr[0]), ((Number) arr[1]).longValue()));

        OperationalEventSummaryResponse resp = new OperationalEventSummaryResponse(
                effectiveDe.truncatedTo(ChronoUnit.SECONDS),
                effectiveAte.truncatedTo(ChronoUnit.SECONDS),
                total,
                porEventType,
                porOrigem
        );
        return ResponseEntity.ok(ApiResponse.success("Resumo eventos operacionais", resp));
    }

    @PostMapping("/eventos/retencao/dry-run")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retentionDryRun() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        long count = retentionService.countOldEvents(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Dry-run retention", Map.of("oldEventsCount", count)));
    }

    @PostMapping("/eventos/retencao/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retentionExecute() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        int deleted = retentionService.cleanupOldEvents(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Retention cleanup executed", Map.of("deleted", deleted)));
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
