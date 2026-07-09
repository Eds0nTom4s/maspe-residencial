package com.restaurante.controller;

import com.restaurante.dto.request.DeviceOfflineSyncBatchRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceOfflineCapabilitiesResponse;
import com.restaurante.dto.response.DeviceOfflineCommandResultResponse;
import com.restaurante.dto.response.DeviceOfflineSyncBatchResponse;
import com.restaurante.dto.response.DeviceOfflineSyncSessionResponse;
import com.restaurante.model.enums.DeviceOfflineSyncSessionStatus;
import com.restaurante.service.device.offline.DeviceOfflineSyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/device/offline-sync")
@RequiredArgsConstructor
@Tag(name = "Device - Offline Sync", description = "Sync batch de comandos offline (MVP) para POS com idempotência por device+clientRequestId")
public class DeviceOfflineSyncController {

    private final DeviceOfflineSyncService offlineSyncService;

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<DeviceOfflineSyncBatchResponse>> syncBatch(
            @Valid @RequestBody DeviceOfflineSyncBatchRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        DeviceOfflineSyncBatchResponse resp = offlineSyncService.syncBatch(request, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Offline sync", resp));
    }

    @GetMapping("/commands/{clientRequestId}")
    public ResponseEntity<ApiResponse<DeviceOfflineCommandResultResponse>> getCommand(@PathVariable String clientRequestId) {
        DeviceOfflineCommandResultResponse resp = offlineSyncService.getCommandByClientRequestId(clientRequestId);
        return ResponseEntity.ok(ApiResponse.success("Command", resp));
    }

    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<DeviceOfflineCapabilitiesResponse>> capabilities() {
        DeviceOfflineCapabilitiesResponse resp = offlineSyncService.capabilities();
        return ResponseEntity.ok(ApiResponse.success("Offline capabilities", resp));
    }

    @GetMapping("/sessions/{serverSyncId}")
    public ResponseEntity<ApiResponse<DeviceOfflineSyncSessionResponse>> getSession(@PathVariable String serverSyncId) {
        DeviceOfflineSyncSessionResponse resp = offlineSyncService.getSessionForDevice(serverSyncId);
        return ResponseEntity.ok(ApiResponse.success("Offline sync session", resp));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<DeviceOfflineSyncSessionResponse>>> listSessions(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) DeviceOfflineSyncSessionStatus status
    ) {
        List<DeviceOfflineSyncSessionResponse> resp = offlineSyncService.listSessionsForDevice(limit, status);
        return ResponseEntity.ok(ApiResponse.success("Offline sync sessions", resp));
    }
}
