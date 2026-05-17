package com.restaurante.controller;

import com.restaurante.dto.response.DeviceBootstrapSyncResponse;
import com.restaurante.dto.response.DeviceCatalogSyncResponse;
import com.restaurante.dto.response.DeviceMesasSyncResponse;
import com.restaurante.dto.response.DeviceProducaoSyncResponse;
import com.restaurante.dto.response.DeviceQrSyncResponse;
import com.restaurante.dto.response.KdsSubPedidoResponse;
import com.restaurante.dto.response.SyncEnvelope;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.DeviceReadOnlySyncService;
import com.restaurante.service.device.DeviceSyncEtagService;
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
import java.util.List;

@RestController
@RequestMapping("/device/sync")
@RequiredArgsConstructor
@Tag(name = "Device - Sync", description = "Sync read-only (bootstrap/catálogo/mesas/QR/produção) para POS/KDS (deviceToken)")
public class DeviceSyncController {

    private final DeviceReadOnlySyncService syncService;
    private final ProducaoKdsService producaoKdsService;
    private final DeviceSyncEtagService etagService;

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof DevicePrincipal dp) {
            return dp;
        }
        throw new DeviceUnauthorizedException("Authorization: Device <token> é obrigatório.");
    }

    @GetMapping("/bootstrap")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceBootstrapSyncResponse>> bootstrap(
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceBootstrapSyncResponse resp = syncService.bootstrap(device);
        String etag = etagService.etagFor("bootstrap|" + device.tenantId() + "|" + device.dispositivoId() + "|" + resp.syncGeneratedAt());
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        SyncEnvelope<DeviceBootstrapSyncResponse> env = SyncEnvelope.incremental(resp, resp.syncGeneratedAt(), "bootstrap:v1", etag, false, false, null, List.of());
        return ResponseEntity.ok().eTag(etag).body(env);
    }

    @GetMapping("/catalogo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceCatalogSyncResponse>> catalogo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        var page = syncService.syncCatalogoPaged(device, updatedSince, includeInactive, cursor, limit);
        DeviceCatalogSyncResponse resp = page.data();
        String etag = etagService.etagFor("catalog|" + device.tenantId() + "|" + includeInactive + "|" + updatedSince + "|" + cursor + "|" + limit + "|" + resp.syncGeneratedAt());
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        SyncEnvelope<DeviceCatalogSyncResponse> env = SyncEnvelope.incremental(resp, resp.syncGeneratedAt(), "catalog:v1", etag, false, page.hasMore(), page.nextCursor(), List.of());
        return ResponseEntity.ok().eTag(etag).body(env);
    }

    @GetMapping("/mesas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceMesasSyncResponse>> mesas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceMesasSyncResponse resp = syncService.syncMesas(device, updatedSince, unidadeAtendimentoId);
        String etag = etagService.etagFor("mesas|" + device.tenantId() + "|" + (unidadeAtendimentoId != null ? unidadeAtendimentoId : device.unidadeAtendimentoId()) + "|" + updatedSince + "|" + resp.mesas().size() + "|" + resp.syncGeneratedAt());
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        SyncEnvelope<DeviceMesasSyncResponse> env = SyncEnvelope.incremental(resp, resp.syncGeneratedAt(), "mesas:v1", etag, false, false, null, List.of());
        return ResponseEntity.ok().eTag(etag).body(env);
    }

    @GetMapping("/qrcodes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceQrSyncResponse>> qrcodes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceQrSyncResponse resp = syncService.syncQrCodes(device, updatedSince);
        String etag = etagService.etagFor("qrcodes|" + device.tenantId() + "|" + device.unidadeAtendimentoId() + "|" + updatedSince + "|" + resp.qrcodes().size() + "|" + resp.syncGeneratedAt());
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        SyncEnvelope<DeviceQrSyncResponse> env = SyncEnvelope.incremental(resp, resp.syncGeneratedAt(), "qrcodes:v1", etag, false, false, null, List.of());
        return ResponseEntity.ok().eTag(etag).body(env);
    }

    @GetMapping("/producao")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceProducaoSyncResponse>> producao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        DeviceProducaoSyncResponse resp = syncService.syncProducao(device, updatedSince);
        String etag = etagService.etagFor("producao|" + device.tenantId() + "|" + updatedSince + "|" + resp.unidadesProducao().size() + "|" + resp.rotas().size() + "|" + resp.syncGeneratedAt());
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        SyncEnvelope<DeviceProducaoSyncResponse> env = SyncEnvelope.incremental(resp, resp.syncGeneratedAt(), "producao:v1", etag, false, false, null, List.of());
        return ResponseEntity.ok().eTag(etag).body(env);
    }

    @GetMapping("/producao/fila")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<Page<KdsSubPedidoResponse>>> filaProducao(
            @RequestParam(required = false) StatusSubPedido status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @RequestParam(required = false) String search,
            Pageable pageable,
            HttpServletRequest http,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        // Exige principal device (não aceita JWT humano como substituto)
        DevicePrincipal device = requireDevicePrincipal();
        Page<KdsSubPedidoResponse> resp = producaoKdsService.listarSubPedidosMinhaUnidade(status, de, ate, search, pageable);
        String etag = etagService.etagFor("fila|" + device.tenantId() + "|" + device.unidadeProducaoId() + "|" + status + "|" + de + "|" + ate + "|" + search + "|" + pageable.getPageNumber() + "|" + pageable.getPageSize() + "|" + resp.getTotalElements());
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        LocalDateTime now = LocalDateTime.now();
        SyncEnvelope<Page<KdsSubPedidoResponse>> env = SyncEnvelope.incremental(resp, now, "fila:v1", etag, false, resp.hasNext(), null, List.of());
        return ResponseEntity.ok().eTag(etag).body(env);
    }
}
