package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplate;
import com.restaurante.device.capability.template.service.DeviceCapabilityRolloutService;
import com.restaurante.device.capability.template.service.DeviceCapabilityTemplateService;
import com.restaurante.dto.request.CreateDeviceCapabilityTemplateRequest;
import com.restaurante.dto.request.DeviceCapabilityRolloutRequest;
import com.restaurante.dto.request.UpdateDeviceCapabilityTemplateRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceCapabilityRolloutApplyResponse;
import com.restaurante.dto.response.DeviceCapabilityRolloutPreviewResponse;
import com.restaurante.dto.response.DeviceCapabilityTemplateResponse;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/device-capability-templates")
@RequiredArgsConstructor
@Tag(name = "Device Capability Templates", description = "Templates e rollout de capabilities por unidade/device-type")
public class TenantDeviceCapabilityTemplateController {

    private final TenantGuard tenantGuard;
    private final DeviceCapabilityTemplateService templateService;
    private final DeviceCapabilityRolloutService rolloutService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DeviceCapabilityTemplateResponse>>> list() {
        List<DeviceCapabilityTemplateResponse> out = templateService.list().stream().map(this::toResponseShallow).toList();
        return ResponseEntity.ok(ApiResponse.success("Templates", out));
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityTemplateResponse>> get(@PathVariable Long templateId) {
        DeviceCapabilityTemplate t = templateService.get(templateId);
        DeviceCapabilityTemplateResponse resp = toResponseFull(t);
        return ResponseEntity.ok(ApiResponse.success("Template", resp));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityTemplateResponse>> create(@Valid @RequestBody CreateDeviceCapabilityTemplateRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        DeviceCapabilityTemplate t = templateService.create(req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Criado", toResponseFull(t)));
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityTemplateResponse>> update(@PathVariable Long templateId, @Valid @RequestBody UpdateDeviceCapabilityTemplateRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        DeviceCapabilityTemplate t = templateService.update(templateId, req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Atualizado", toResponseFull(t)));
    }

    @PostMapping("/{templateId}/activate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityTemplateResponse>> activate(@PathVariable Long templateId, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        DeviceCapabilityTemplate t = templateService.activate(templateId, true, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Ativado", toResponseFull(t)));
    }

    @PostMapping("/{templateId}/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityTemplateResponse>> deactivate(@PathVariable Long templateId, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        DeviceCapabilityTemplate t = templateService.activate(templateId, false, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Desativado", toResponseFull(t)));
    }

    @PostMapping("/{templateId}/rollout/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityRolloutPreviewResponse>> preview(@PathVariable Long templateId, @Valid @RequestBody DeviceCapabilityRolloutRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        DeviceCapabilityRolloutPreviewResponse resp = rolloutService.preview(templateId, req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Preview", resp));
    }

    @PostMapping("/{templateId}/rollout/apply")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCapabilityRolloutApplyResponse>> apply(@PathVariable Long templateId, @Valid @RequestBody DeviceCapabilityRolloutRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        DeviceCapabilityRolloutApplyResponse resp = rolloutService.apply(templateId, req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Applied", resp));
    }

    private DeviceCapabilityTemplateResponse toResponseShallow(DeviceCapabilityTemplate t) {
        DeviceCapabilityTemplateResponse r = new DeviceCapabilityTemplateResponse();
        r.setTemplateId(t.getId());
        r.setCode(t.getCode());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setTargetDeviceType(t.getTargetDeviceType());
        r.setStatus(t.getStatus());
        r.setSystemDefault(t.isSystemDefault());
        r.setVersion(t.getVersion());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        r.setItems(List.of());
        return r;
    }

    private DeviceCapabilityTemplateResponse toResponseFull(DeviceCapabilityTemplate t) {
        DeviceCapabilityTemplateResponse r = toResponseShallow(t);
        var items = templateService.listItems(t.getId()).stream().map(i -> {
            DeviceCapabilityTemplateResponse.DeviceCapabilityTemplateItemResponse ir = new DeviceCapabilityTemplateResponse.DeviceCapabilityTemplateItemResponse();
            ir.setId(i.getId());
            ir.setCapability(i.getCapability());
            ir.setEnabled(i.isEnabled());
            ir.setOverrideReason(i.getOverrideReason());
            try {
                ir.setMetadata(i.getMetadataJson() != null ? objectMapper.readTree(i.getMetadataJson()) : null);
            } catch (Exception ignored) { }
            return ir;
        }).toList();
        r.setItems(items);
        return r;
    }
}

