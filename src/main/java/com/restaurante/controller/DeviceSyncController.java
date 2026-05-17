package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceBootstrapSyncResponse;
import com.restaurante.dto.response.DeviceCatalogSyncResponse;
import com.restaurante.dto.response.DeviceMesasSyncResponse;
import com.restaurante.dto.response.DeviceProducaoSyncResponse;
import com.restaurante.dto.response.DeviceQrSyncResponse;
import com.restaurante.dto.response.KdsSubPedidoResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.DeviceReadOnlySyncService;
import com.restaurante.service.producao.ProducaoKdsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/device/sync")
@RequiredArgsConstructor
@Tag(name = "Device - Sync", description = "Sync read-only (bootstrap/catálogo/mesas/QR/produção) para POS/KDS (deviceToken)")
public class DeviceSyncController {

    private final DeviceReadOnlySyncService syncService;
    private final ProducaoKdsService producaoKdsService;

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof DevicePrincipal dp) {
            return dp;
        }
        throw new DeviceUnauthorizedException("Authorization: Device <token> é obrigatório.");
    }

    @GetMapping("/bootstrap")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceBootstrapSyncResponse>> bootstrap() {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceBootstrapSyncResponse resp = syncService.bootstrap(device);
        return ResponseEntity.ok(ApiResponse.success("Bootstrap", resp));
    }

    @GetMapping("/catalogo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceCatalogSyncResponse>> catalogo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceCatalogSyncResponse resp = syncService.syncCatalogo(device, updatedSince, includeInactive);
        return ResponseEntity.ok(ApiResponse.success("Catálogo", resp));
    }

    @GetMapping("/mesas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceMesasSyncResponse>> mesas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false) Long unidadeAtendimentoId
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceMesasSyncResponse resp = syncService.syncMesas(device, updatedSince, unidadeAtendimentoId);
        return ResponseEntity.ok(ApiResponse.success("Mesas", resp));
    }

    @GetMapping("/qrcodes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceQrSyncResponse>> qrcodes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceQrSyncResponse resp = syncService.syncQrCodes(device, updatedSince);
        return ResponseEntity.ok(ApiResponse.success("QR Codes", resp));
    }

    @GetMapping("/producao")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceProducaoSyncResponse>> producao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceProducaoSyncResponse resp = syncService.syncProducao(device, updatedSince);
        return ResponseEntity.ok(ApiResponse.success("Produção", resp));
    }

    @GetMapping("/producao/fila")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<KdsSubPedidoResponse>>> filaProducao(
            @RequestParam(required = false) StatusSubPedido status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @RequestParam(required = false) String search,
            Pageable pageable,
            HttpServletRequest http
    ) {
        // Exige principal device (não aceita JWT humano como substituto)
        requireDevicePrincipal();
        Page<KdsSubPedidoResponse> resp = producaoKdsService.listarSubPedidosMinhaUnidade(status, de, ate, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Fila de produção", resp));
    }
}

