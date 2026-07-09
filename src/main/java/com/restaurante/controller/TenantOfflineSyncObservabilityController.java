package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantOfflineSyncCommandSummaryResponse;
import com.restaurante.dto.response.TenantOfflineSyncMetricsResponse;
import com.restaurante.dto.response.TenantOfflineSyncSessionDetailResponse;
import com.restaurante.dto.response.TenantOfflineSyncSessionListItemResponse;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.service.tenant.offline.TenantOfflineSyncObservabilityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/tenant/offline-sync")
@RequiredArgsConstructor
@Tag(name = "Tenant - Offline Sync Observability", description = "Observabilidade e troubleshooting de sync offline por sessões (Prompt 40.2)")
public class TenantOfflineSyncObservabilityController {

    private final TenantOfflineSyncObservabilityService service;

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TenantOfflineSyncSessionListItemResponse>>> listSessions(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String appVersion,
            @RequestParam(required = false) Boolean hasFailures,
            @RequestParam(required = false) Boolean hasConflicts,
            @RequestParam(required = false) Boolean hasRejected,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            Pageable pageable
    ) {
        Page<TenantOfflineSyncSessionListItemResponse> page = service.listSessions(
                unidadeId, deviceId, status, appVersion, dateFrom, dateTo,
                hasFailures, hasConflicts, hasRejected, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Offline sync sessions", page));
    }

    @GetMapping("/sessions/{serverSyncId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantOfflineSyncSessionDetailResponse>> getSession(
            @PathVariable String serverSyncId,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        TenantOfflineSyncSessionDetailResponse out = service.getSession(serverSyncId, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Offline sync session", out));
    }

    @GetMapping("/sessions/{serverSyncId}/commands")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TenantOfflineSyncCommandSummaryResponse>>> listCommands(
            @PathVariable String serverSyncId,
            @RequestParam(required = false) DeviceOfflineCommandStatus status,
            @RequestParam(required = false) DeviceOfflineCommandType commandType,
            @RequestParam(required = false) String conflictCode,
            @RequestParam(required = false) String errorCode,
            Pageable pageable
    ) {
        Page<TenantOfflineSyncCommandSummaryResponse> page = service.listCommands(serverSyncId, status, commandType, conflictCode, errorCode, pageable);
        return ResponseEntity.ok(ApiResponse.success("Offline sync commands", page));
    }

    @GetMapping("/metrics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantOfflineSyncMetricsResponse>> metrics(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo
    ) {
        TenantOfflineSyncMetricsResponse out = service.metrics(unidadeId, deviceId, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success("Offline sync metrics", out));
    }
}

