package com.restaurante.controller;

import com.restaurante.dto.response.DeviceBootstrapSyncResponse;
import com.restaurante.dto.response.DeviceCatalogSyncResponse;
import com.restaurante.dto.response.DeviceFilaDiffSyncResponse;
import com.restaurante.dto.response.DeviceMesasSyncResponse;
import com.restaurante.dto.response.DeviceProducaoSyncResponse;
import com.restaurante.dto.response.DeviceQrSyncResponse;
import com.restaurante.dto.response.KdsSubPedidoResponse;
import com.restaurante.dto.response.SyncEnvelope;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.DeviceFilaDiffService;
import com.restaurante.service.device.DeviceReadOnlySyncService;
import com.restaurante.service.device.DeviceSyncVersionService;
import com.restaurante.service.producao.ProducaoKdsService;
import com.restaurante.service.operacional.OperationalCapabilitiesPolicy;
import com.restaurante.service.metrics.DeviceSyncMetricsService;
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
    private final OperationalCapabilitiesPolicy operationalCapabilitiesPolicy;
    private final DeviceSyncVersionService versionService;
    private final DeviceFilaDiffService filaDiffService;
    private final DeviceSyncMetricsService metrics;

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
        return metrics.timeSync("BOOTSTRAP", () -> {
            DevicePrincipal device = requireDevicePrincipal();
            DeviceBootstrapSyncResponse resp = syncService.bootstrap(device);
            var ver = versionService.computeBootstrap(device);
            String etag = ver.etag();
            if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                metrics.recordEtagHit("BOOTSTRAP");
                metrics.recordSyncRequest("BOOTSTRAP", "304");
                return ResponseEntity.status(304).eTag(etag).build();
            }
            metrics.recordEtagMiss("BOOTSTRAP");
            metrics.recordSyncRequest("BOOTSTRAP", "200");
            SyncEnvelope<DeviceBootstrapSyncResponse> env = SyncEnvelope.incremental(
                    resp,
                    resp.syncGeneratedAt(),
                    ver.syncVersion(),
                    etag,
                    ver.fullSyncRequired(),
                    ver.fullSyncReason(),
                    false,
                    null,
                    ver.warnings()
            );
            return ResponseEntity.ok().eTag(etag).body(env);
        });
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
        return metrics.timeSync("CATALOGO", () -> {
            DevicePrincipal device = requireDevicePrincipal();
            var ver = versionService.computeCatalog(device, includeInactive, updatedSince);
            if (ver.fullSyncRequired()) {
                metrics.recordFullSyncRequired("CATALOGO", ver.fullSyncReason());
            }
            LocalDateTime effectiveUpdatedSince = ver.fullSyncRequired() ? null : updatedSince;
            var page = syncService.syncCatalogoPaged(device, effectiveUpdatedSince, includeInactive, cursor, limit);
            DeviceCatalogSyncResponse resp = page.data();
            String etag = ver.etag();
            boolean allowNotModified = cursor == null || cursor.isBlank();
            if (allowNotModified && !ver.fullSyncRequired() && ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                metrics.recordEtagHit("CATALOGO");
                metrics.recordSyncRequest("CATALOGO", "304");
                return ResponseEntity.status(304).eTag(etag).build();
            }
            metrics.recordEtagMiss("CATALOGO");
            metrics.recordSyncRequest("CATALOGO", "200");
            boolean fullRequired = ver.fullSyncRequired() || page.cursorExpired();
            SyncEnvelope.FullSyncRequiredReason reason = ver.fullSyncRequired() ? ver.fullSyncReason()
                    : (page.cursorExpired() ? SyncEnvelope.FullSyncRequiredReason.CURSOR_EXPIRED : SyncEnvelope.FullSyncRequiredReason.NONE);
            List<SyncEnvelope.SyncWarning> warnings = new java.util.ArrayList<>(ver.warnings());
            if (page.cursorExpired()) {
                warnings.add(new SyncEnvelope.SyncWarning(SyncEnvelope.SyncWarningCode.CURSOR_EXPIRED, "Cursor expirado; reiniciando paginação."));
            }
            SyncEnvelope<DeviceCatalogSyncResponse> env = SyncEnvelope.incremental(
                    resp,
                    resp.syncGeneratedAt(),
                    ver.syncVersion(),
                    etag,
                    fullRequired,
                    reason,
                    page.hasMore(),
                    page.nextCursor(),
                    warnings
            );
            var builder = ResponseEntity.ok();
            if (allowNotModified) {
                builder.eTag(etag);
            }
            return builder.body(env);
        });
    }

    @GetMapping("/mesas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceMesasSyncResponse>> mesas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        return metrics.timeSync("MESAS", () -> {
            DevicePrincipal device = requireDevicePrincipal();
            var ver = versionService.computeMesas(device, unidadeAtendimentoId, updatedSince);
            if (ver.fullSyncRequired()) {
                metrics.recordFullSyncRequired("MESAS", ver.fullSyncReason());
            }
            LocalDateTime effectiveUpdatedSince = ver.fullSyncRequired() ? null : updatedSince;
            var page = syncService.syncMesasPaged(device, effectiveUpdatedSince, unidadeAtendimentoId, cursor, limit);
            DeviceMesasSyncResponse resp = page.data();
            String etag = ver.etag();
            boolean allowNotModified = cursor == null || cursor.isBlank();
            if (allowNotModified && !ver.fullSyncRequired() && ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                metrics.recordEtagHit("MESAS");
                metrics.recordSyncRequest("MESAS", "304");
                return ResponseEntity.status(304).eTag(etag).build();
            }
            metrics.recordEtagMiss("MESAS");
            metrics.recordSyncRequest("MESAS", "200");
            List<SyncEnvelope.SyncWarning> warnings = new java.util.ArrayList<>(ver.warnings());
            if (page.cursorExpired()) {
                warnings.add(new SyncEnvelope.SyncWarning(SyncEnvelope.SyncWarningCode.CURSOR_EXPIRED, "Cursor expirado; reiniciando paginação."));
            }
            SyncEnvelope<DeviceMesasSyncResponse> env = SyncEnvelope.incremental(
                    resp,
                    resp.syncGeneratedAt(),
                    ver.syncVersion(),
                    etag,
                    ver.fullSyncRequired(),
                    ver.fullSyncReason(),
                    page.hasMore(),
                    page.nextCursor(),
                    warnings
            );
            var builder = ResponseEntity.ok();
            if (allowNotModified) {
                builder.eTag(etag);
            }
            return builder.body(env);
        });
    }

    @GetMapping("/qrcodes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceQrSyncResponse>> qrcodes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        return metrics.timeSync("QRCODES", () -> {
            DevicePrincipal device = requireDevicePrincipal();
            var ver = versionService.computeQrCodes(device, updatedSince);
            if (ver.fullSyncRequired()) {
                metrics.recordFullSyncRequired("QRCODES", ver.fullSyncReason());
            }
            LocalDateTime effectiveUpdatedSince = ver.fullSyncRequired() ? null : updatedSince;
            var page = syncService.syncQrCodesPaged(device, effectiveUpdatedSince, cursor, limit);
            DeviceQrSyncResponse resp = page.data();
            String etag = ver.etag();
            boolean allowNotModified = cursor == null || cursor.isBlank();
            if (allowNotModified && !ver.fullSyncRequired() && ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                metrics.recordEtagHit("QRCODES");
                metrics.recordSyncRequest("QRCODES", "304");
                return ResponseEntity.status(304).eTag(etag).build();
            }
            metrics.recordEtagMiss("QRCODES");
            metrics.recordSyncRequest("QRCODES", "200");
            List<SyncEnvelope.SyncWarning> warnings = new java.util.ArrayList<>(ver.warnings());
            if (page.cursorExpired()) {
                warnings.add(new SyncEnvelope.SyncWarning(SyncEnvelope.SyncWarningCode.CURSOR_EXPIRED, "Cursor expirado; reiniciando paginação."));
            }
            SyncEnvelope<DeviceQrSyncResponse> env = SyncEnvelope.incremental(
                    resp,
                    resp.syncGeneratedAt(),
                    ver.syncVersion(),
                    etag,
                    ver.fullSyncRequired(),
                    ver.fullSyncReason(),
                    page.hasMore(),
                    page.nextCursor(),
                    warnings
            );
            var builder = ResponseEntity.ok();
            if (allowNotModified) {
                builder.eTag(etag);
            }
            return builder.body(env);
        });
    }

    @GetMapping("/producao")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceProducaoSyncResponse>> producao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @org.springframework.web.bind.annotation.RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        return metrics.timeSync("PRODUCAO", () -> {
            DevicePrincipal device = requireDevicePrincipal();
            operationalCapabilitiesPolicy.assertProductionEnabled(device.tenantId());
            var ver = versionService.computeProducao(device, updatedSince);
            if (ver.fullSyncRequired()) {
                metrics.recordFullSyncRequired("PRODUCAO", ver.fullSyncReason());
            }
            LocalDateTime effectiveUpdatedSince = ver.fullSyncRequired() ? null : updatedSince;
            DeviceProducaoSyncResponse resp = syncService.syncProducao(device, effectiveUpdatedSince);
            String etag = ver.etag();
            if (!ver.fullSyncRequired() && ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                metrics.recordEtagHit("PRODUCAO");
                metrics.recordSyncRequest("PRODUCAO", "304");
                return ResponseEntity.status(304).eTag(etag).build();
            }
            metrics.recordEtagMiss("PRODUCAO");
            metrics.recordSyncRequest("PRODUCAO", "200");
            SyncEnvelope<DeviceProducaoSyncResponse> env = SyncEnvelope.incremental(
                    resp,
                    resp.syncGeneratedAt(),
                    ver.syncVersion(),
                    etag,
                    ver.fullSyncRequired(),
                    ver.fullSyncReason(),
                    false,
                    null,
                    ver.warnings()
            );
            return ResponseEntity.ok().eTag(etag).body(env);
        });
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
        return metrics.timeSync("PRODUCAO_FILA", () -> {
            DevicePrincipal device = requireDevicePrincipal();
            operationalCapabilitiesPolicy.assertProductionEnabled(device.tenantId());
            Long unidadeProducaoId = device.unidadeProducaoId();
            if (unidadeProducaoId == null) {
                throw new com.restaurante.exception.ConflictException("DEVICE_PRODUCTION_UNIT_AMBIGUOUS");
            }

            var ver = versionService.computeFila(device, unidadeProducaoId, status, de, ate, search);
            Page<KdsSubPedidoResponse> resp = producaoKdsService.listarSubPedidosMinhaUnidade(status, de, ate, search, pageable);
            String etag = ver.etag();
            if (!ver.fullSyncRequired() && ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                metrics.recordEtagHit("PRODUCAO_FILA");
                metrics.recordSyncRequest("PRODUCAO_FILA", "304");
                return ResponseEntity.status(304).eTag(etag).build();
            }
            metrics.recordEtagMiss("PRODUCAO_FILA");
            metrics.recordSyncRequest("PRODUCAO_FILA", "200");
            LocalDateTime now = LocalDateTime.now();
            SyncEnvelope<Page<KdsSubPedidoResponse>> env = SyncEnvelope.incremental(
                    resp,
                    now,
                    ver.syncVersion(),
                    etag,
                    ver.fullSyncRequired(),
                    ver.fullSyncReason(),
                    resp.hasNext(),
                    null,
                    ver.warnings()
            );
            return ResponseEntity.ok().eTag(etag).body(env);
        });
    }

    @GetMapping("/producao/fila/diff")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncEnvelope<DeviceFilaDiffSyncResponse>> filaProducaoDiff(
            @RequestParam(required = false) Long sinceEventId,
            @RequestParam(required = false) Integer limit
    ) {
        return metrics.timeSync("PRODUCAO_FILA_DIFF", () -> {
            DevicePrincipal device = requireDevicePrincipal();
            var result = filaDiffService.diff(device, sinceEventId, limit);
            if (result.fullSyncRequired()) {
                metrics.recordFullSyncRequired("PRODUCAO_FILA_DIFF", result.fullSyncReason());
            }
            DeviceFilaDiffSyncResponse resp = result.data();
            metrics.recordSyncRequest("PRODUCAO_FILA_DIFF", "200");
            SyncEnvelope<DeviceFilaDiffSyncResponse> env = SyncEnvelope.incremental(
                    resp,
                    resp.syncGeneratedAt(),
                    "fila-diff:v1",
                    null,
                    result.fullSyncRequired(),
                    result.fullSyncReason(),
                    resp.hasMore(),
                    null,
                    result.warnings()
            );
            return ResponseEntity.ok().body(env);
        });
    }
}
