package com.restaurante.controller;

import com.restaurante.device.offline.entity.DeviceOfflineCommandReplayAttempt;
import com.restaurante.dto.request.OfflineCommandReplayPreviewRequest;
import com.restaurante.dto.request.OfflineCommandReplayRequest;
import com.restaurante.dto.request.OfflineSingleCommandReplayRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.OfflineCommandReplayBatchResponse;
import com.restaurante.dto.response.OfflineCommandReplayPreviewResponse;
import com.restaurante.dto.response.OfflineCommandReplayResultResponse;
import com.restaurante.dto.response.OfflineSyncDiagnosticExportResponse;
import com.restaurante.service.tenant.offline.TenantOfflineSyncReplayService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/offline-sync")
@RequiredArgsConstructor
@Tag(name = "Tenant - Offline Sync Replay", description = "Replay controlado por sync session + export diagnóstico sanitizado (Prompt 40.3)")
public class TenantOfflineSyncReplayController {

    private final TenantOfflineSyncReplayService replayService;

    @PostMapping("/sessions/{serverSyncId}/replay/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OfflineCommandReplayPreviewResponse>> preview(
            @PathVariable String serverSyncId,
            @Valid @RequestBody OfflineCommandReplayPreviewRequest req,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OfflineCommandReplayPreviewResponse out = replayService.preview(
                serverSyncId,
                req.getStatuses(),
                req.getCommandTypes(),
                Boolean.TRUE.equals(req.getOnlyEligible()),
                Boolean.TRUE.equals(req.getIncludeWarnings()),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Replay preview", out));
    }

    @PostMapping("/sessions/{serverSyncId}/replay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OfflineCommandReplayBatchResponse>> replay(
            @PathVariable String serverSyncId,
            @Valid @RequestBody OfflineCommandReplayRequest req,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OfflineCommandReplayBatchResponse out = replayService.replaySession(
                serverSyncId,
                req.getCommandIds(),
                req.getStatuses(),
                req.getCommandTypes(),
                req.getReason(),
                Boolean.TRUE.equals(req.getDryRun()),
                Boolean.TRUE.equals(req.getForce()),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Replay", out));
    }

    @PostMapping("/commands/{commandId}/replay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OfflineCommandReplayResultResponse>> replaySingle(
            @PathVariable Long commandId,
            @Valid @RequestBody OfflineSingleCommandReplayRequest req,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OfflineCommandReplayResultResponse out = replayService.replaySingleCommand(
                commandId,
                req.getReason(),
                Boolean.TRUE.equals(req.getForce()),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Replay command", out));
    }

    @GetMapping("/sessions/{serverSyncId}/replay-attempts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<DeviceOfflineCommandReplayAttempt>>> attemptsForSession(
            @PathVariable String serverSyncId,
            Pageable pageable
    ) {
        Page<DeviceOfflineCommandReplayAttempt> out = replayService.listAttemptsForSession(serverSyncId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Replay attempts", out));
    }

    @GetMapping("/commands/{commandId}/replay-attempts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<DeviceOfflineCommandReplayAttempt>>> attemptsForCommand(
            @PathVariable Long commandId,
            Pageable pageable
    ) {
        Page<DeviceOfflineCommandReplayAttempt> out = replayService.listAttemptsForCommand(commandId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Replay attempts", out));
    }

    @GetMapping("/sessions/{serverSyncId}/diagnostic-export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OfflineSyncDiagnosticExportResponse>> export(
            @PathVariable String serverSyncId,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OfflineSyncDiagnosticExportResponse out = replayService.exportDiagnostic(serverSyncId, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Diagnostic export", out));
    }
}
