package com.restaurante.controller;

import com.restaurante.device.capability.service.TenantDeviceCapabilityService;
import com.restaurante.dto.request.UpdateDeviceCapabilityRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceCapabilityResponse;
import com.restaurante.model.enums.DeviceCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/devices/{deviceId}/capabilities")
@RequiredArgsConstructor
@Tag(name = "Tenant Device Capabilities", description = "Configuração de capabilities operacionais por device (sensível)")
public class TenantDeviceCapabilityController {

    private final TenantDeviceCapabilityService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DeviceCapabilityResponse>>> list(@PathVariable Long deviceId) {
        List<DeviceCapabilityResponse> resp = service.list(deviceId).stream().map(e -> {
            DeviceCapabilityResponse r = new DeviceCapabilityResponse();
            r.setCapability(e.getCapability());
            r.setEnabled(e.isEnabled());
            r.setSource(e.getSource());
            r.setCreatedAt(e.getCreatedAt());
            r.setUpdatedAt(e.getUpdatedAt());
            return r;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success("Capabilities do device", resp));
    }

    @PutMapping("/{capability}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityResponse>> upsert(
            @PathVariable Long deviceId,
            @PathVariable DeviceCapability capability,
            @Valid @RequestBody UpdateDeviceCapabilityRequest req,
            HttpServletRequest http
    ) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        var saved = service.upsert(deviceId, capability, req, ip, ua);
        DeviceCapabilityResponse r = new DeviceCapabilityResponse();
        r.setCapability(saved.getCapability());
        r.setEnabled(saved.isEnabled());
        r.setSource(saved.getSource());
        r.setCreatedAt(saved.getCreatedAt());
        r.setUpdatedAt(saved.getUpdatedAt());
        return ResponseEntity.ok(ApiResponse.success("Capability atualizada", r));
    }
}

