package com.restaurante.controller;

import com.restaurante.consumo.participante.service.SessaoParticipanteExtendedListService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.SessaoParticipantePageResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prompt 41.4 — Endpoints device para listagens ampliadas e health check do job.
 */
@RestController
@RequestMapping("/device/sessoes-consumo/{sessaoId}/participantes")
@RequiredArgsConstructor
@Tag(name = "Device - Participantes Extended (41.4)", description = "Listagens ampliadas e health check do job de expiração")
public class DeviceSessaoParticipanteExtendedController {

    private final SessaoParticipanteExtendedListService extendedListService;

    /**
     * Listagem unificada de participantes com filtros e paginação.
     * Requer capability VIEW_SESSION_PARTICIPANTS.
     */
    @GetMapping("/all")
    @Operation(summary = "Listagem paginada e filtrada de todos os participantes (device)")
    public ResponseEntity<ApiResponse<SessaoParticipantePageResponse>> listAll(
            @PathVariable Long sessaoId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        DevicePrincipal device = requireDevice();
        requireCapability(device, DeviceCapability.VIEW_SESSION_PARTICIPANTS);

        SessaoParticipanteStatus statusFilter = parseStatus(status);
        SessaoParticipanteRole roleFilter = parseRole(role);

        Long tenantId = device.tenantId();

        var result = extendedListService.listAllByDevice(
                tenantId, sessaoId, statusFilter, roleFilter, page, size);

        return ResponseEntity.ok(ApiResponse.success("Participantes", new SessaoParticipantePageResponse(result)));
    }

    /**
     * Listagem ampliada de pendências/convites.
     * Requer capability VIEW_PENDING_SESSION_PARTICIPANTS.
     */
    @GetMapping("/pending-all")
    @Operation(summary = "Listagem ampliada de pendências/convites (device)")
    public ResponseEntity<ApiResponse<SessaoParticipantePageResponse>> listPendingAll(
            @PathVariable Long sessaoId,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Boolean canResend,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        DevicePrincipal device = requireDevice();
        requireCapability(device, DeviceCapability.VIEW_PENDING_SESSION_PARTICIPANTS);

        Long tenantId = device.tenantId();

        var result = extendedListService.listPendingAllByDevice(
                tenantId, sessaoId, statuses, canResend, page, size);

        return ResponseEntity.ok(ApiResponse.success("Pendências", new SessaoParticipantePageResponse(result)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DevicePrincipal requireDevice() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DevicePrincipal d)) {
            throw new DeviceUnauthorizedException("Device authentication required");
        }
        return d;
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability cap) {
        if (device.capabilities() == null || !device.capabilities().contains(cap)) {
            throw new com.restaurante.exception.BusinessException("DEVICE_CAPABILITY_REQUIRED");
        }
    }

    private SessaoParticipanteStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return SessaoParticipanteStatus.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new com.restaurante.exception.BusinessException("INVALID_PARTICIPANT_STATUS_FILTER"); }
    }

    private SessaoParticipanteRole parseRole(String s) {
        if (s == null || s.isBlank()) return null;
        try { return SessaoParticipanteRole.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new com.restaurante.exception.BusinessException("INVALID_PARTICIPANT_ROLE_FILTER"); }
    }
}
