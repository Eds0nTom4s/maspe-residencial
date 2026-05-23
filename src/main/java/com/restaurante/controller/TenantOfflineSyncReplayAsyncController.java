package com.restaurante.controller;

import com.restaurante.dto.request.OfflineReplayAsyncRerunRequest;
import com.restaurante.dto.request.OfflineReplayAsyncSubmitRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.OfflineReplayAsyncSubmitResponse;
import com.restaurante.dto.response.OfflineReplayOperationItemResponse;
import com.restaurante.dto.response.OfflineReplayOperationResponse;
import com.restaurante.model.enums.DeviceOfflineReplayOperationItemStatus;
import com.restaurante.service.tenant.offline.TenantOfflineSyncReplayAsyncService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/offline-sync")
@RequiredArgsConstructor
@Tag(name = "Tenant - Offline Replay Async", description = "Replay assíncrono com operationId + progress + rate-limit (Prompt 40.4)")
public class TenantOfflineSyncReplayAsyncController {

    private final TenantOfflineSyncReplayAsyncService asyncService;

    @PostMapping("/sessions/{serverSyncId}/replay/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OfflineReplayAsyncSubmitResponse>> submit(
            @PathVariable String serverSyncId,
            @Valid @RequestBody OfflineReplayAsyncSubmitRequest req,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OfflineReplayAsyncSubmitResponse out = asyncService.submit(
                serverSyncId,
                req.getCommandIds(),
                req.getStatuses(),
                req.getCommandTypes(),
                req.getReason(),
                Boolean.TRUE.equals(req.getForce()),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Replay async submitted", out));
    }

    @GetMapping("/replay-operations/{operationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OfflineReplayOperationResponse>> status(
            @PathVariable String operationId
    ) {
        OfflineReplayOperationResponse out = asyncService.getOperation(operationId);
        return ResponseEntity.ok(ApiResponse.success("Replay operation", out));
    }

    @GetMapping("/replay-operations/{operationId}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<OfflineReplayOperationItemResponse>>> items(
            @PathVariable String operationId,
            @RequestParam(name = "status", required = false) List<DeviceOfflineReplayOperationItemStatus> statuses,
            Pageable pageable
    ) {
        Page<OfflineReplayOperationItemResponse> out = asyncService.listItems(operationId, statuses, pageable);
        return ResponseEntity.ok(ApiResponse.success("Replay operation items", out));
    }

    @PostMapping("/replay-operations/{operationId}/rerun")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OfflineReplayOperationResponse>> rerun(
            @PathVariable String operationId,
            @Valid @RequestBody OfflineReplayAsyncRerunRequest req,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        OfflineReplayOperationResponse out = asyncService.rerun(
                operationId,
                req.getOnlyStatuses(),
                req.getReason(),
                Boolean.TRUE.equals(req.getForce()),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Replay operation rerun", out));
    }
}

