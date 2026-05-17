package com.restaurante.controller;

import com.restaurante.dto.request.DeviceActivationRequest;
import com.restaurante.dto.request.DeviceHeartbeatRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceActivationResponse;
import com.restaurante.dto.response.DeviceConfigResponse;
import com.restaurante.dto.response.DeviceHeartbeatResponse;
import com.restaurante.dto.response.DeviceTokenRotateResponse;
import com.restaurante.service.device.DeviceActivationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
@Tag(name = "Device", description = "Endpoints para ativação e operação básica de dispositivos (sem JWT de usuário)")
public class DeviceController {

    private final DeviceActivationService deviceActivationService;

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<DeviceActivationResponse>> activate(
            @Valid @RequestBody DeviceActivationRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        DeviceActivationResponse resp = deviceActivationService.ativar(request, ua, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Device ativado", resp));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<DeviceHeartbeatResponse>> heartbeat(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody(required = false) DeviceHeartbeatRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        DeviceHeartbeatResponse resp = deviceActivationService.heartbeat(authorization, request, ua, ip);
        return ResponseEntity.ok(ApiResponse.success("Heartbeat OK", resp));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<DeviceConfigResponse>> config(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        DeviceConfigResponse resp = deviceActivationService.config(authorization, ua, ip);
        return ResponseEntity.ok(ApiResponse.success("Config", resp));
    }

    @PostMapping("/token/rotate")
    public ResponseEntity<ApiResponse<DeviceTokenRotateResponse>> rotateToken(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        DeviceTokenRotateResponse resp = deviceActivationService.rotateToken(authorization, ua, ip);
        return ResponseEntity.ok(ApiResponse.success("Token rotacionado", resp));
    }
}
