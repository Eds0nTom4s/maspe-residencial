package com.restaurante.controller.advice;

import com.restaurante.dto.response.SyncEnvelope;
import com.restaurante.dto.response.SyncErrorResponse;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.DeviceForbiddenException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.exception.BusinessException;
import com.restaurante.service.device.DeviceSyncCursorService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

import com.restaurante.service.metrics.DeviceSyncMetricsService;
import org.springframework.beans.factory.ObjectProvider;
import com.restaurante.service.metrics.NoOpDeviceSyncMetricsService;

@RestControllerAdvice(assignableTypes = {
    com.restaurante.controller.DeviceSyncController.class,
    com.restaurante.controller.DeviceOfflineSyncController.class
})
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class DeviceSyncExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceSyncExceptionHandler.class);
    private final DeviceSyncMetricsService metrics;

    public DeviceSyncExceptionHandler(ObjectProvider<DeviceSyncMetricsService> metricsProvider) {
        this.metrics = metricsProvider.getIfAvailable(NoOpDeviceSyncMetricsService::new);
    }

    private boolean isDeviceSyncPath(HttpServletRequest req) {
        String uri = req != null ? req.getRequestURI() : null;
        return uri != null && (uri.startsWith("/device/sync") || uri.startsWith("/api/device/sync"));
    }

    private ResponseEntity<SyncErrorResponse> respond(HttpStatus status, SyncErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Object> delegateToGlobal(Exception ex, HttpServletRequest req, HttpStatus status, String error, String code) {
        var body = com.restaurante.exception.GlobalExceptionHandler.ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(ex.getMessage())
                .code(code)
                .path(req != null ? req.getRequestURI() : "")
                .build();
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(DeviceSyncCursorService.SyncCursorException.class)
    public ResponseEntity<Object> handleCursor(DeviceSyncCursorService.SyncCursorException ex, HttpServletRequest req) {
        if (!isDeviceSyncPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.BAD_REQUEST, "Cursor inválido", ex.getCode().name());
        }
        metrics.recordCursorError("UNKNOWN", ex.getCode());
        log.info("device_sync_cursor_error code={}", ex.getCode());
        SyncErrorResponse body = new SyncErrorResponse(
                ex.getCode(),
                "Cursor de sincronização inválido.",
                null,
                true,
                SyncEnvelope.FullSyncRequiredReason.VERSION_MISMATCH,
                true,
                SyncErrorResponse.SyncRecoveryAction.FULL_SYNC,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DeviceUnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorized(DeviceUnauthorizedException ex, HttpServletRequest req) {
        if (!isDeviceSyncPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.UNAUTHORIZED, "Não autenticado", "DEVICE_UNAUTHORIZED");
        }
        log.info("device_sync_unauthorized");
        SyncErrorResponse body = new SyncErrorResponse(
                SyncErrorResponse.SyncErrorCode.SYNC_DEVICE_UNAUTHORIZED,
                ex.getMessage(),
                null,
                false,
                SyncEnvelope.FullSyncRequiredReason.NONE,
                true,
                SyncErrorResponse.SyncRecoveryAction.REAUTH_DEVICE,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(DeviceForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(DeviceForbiddenException ex, HttpServletRequest req) {
        if (!isDeviceSyncPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.FORBIDDEN, "Acesso negado", "DEVICE_FORBIDDEN");
        }
        log.info("device_sync_forbidden");
        SyncErrorResponse body = new SyncErrorResponse(
                SyncErrorResponse.SyncErrorCode.SYNC_CAPABILITY_FORBIDDEN,
                "Dispositivo sem permissão para este domínio de sincronização.",
                null,
                false,
                SyncEnvelope.FullSyncRequiredReason.NONE,
                false,
                SyncErrorResponse.SyncRecoveryAction.CONTACT_SUPPORT,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex, HttpServletRequest req) {
        if (!isDeviceSyncPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.CONFLICT, "Conflito", null);
        }
        log.info("device_sync_conflict code={}", ex.getMessage());
        SyncErrorResponse body = new SyncErrorResponse(
                SyncErrorResponse.SyncErrorCode.SYNC_SCOPE_AMBIGUOUS,
                "Escopo do dispositivo ambíguo.",
                null,
                true,
                SyncEnvelope.FullSyncRequiredReason.DOMAIN_REQUIRES_FULL_SYNC,
                true,
                SyncErrorResponse.SyncRecoveryAction.FULL_SYNC,
                LocalDateTime.now(),
                Map.of("error", ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex, HttpServletRequest req) {
        if (!isDeviceSyncPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.BAD_REQUEST, "Erro de negócio", null);
        }
        log.info("device_sync_business");
        SyncErrorResponse body = new SyncErrorResponse(
                SyncErrorResponse.SyncErrorCode.SYNC_INTERNAL_ERROR,
                ex.getMessage(),
                null,
                false,
                SyncEnvelope.FullSyncRequiredReason.NONE,
                true,
                SyncErrorResponse.SyncRecoveryAction.RETRY,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOther(Exception ex, HttpServletRequest req) {
        if (!isDeviceSyncPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", null);
        }
        log.warn("device_sync_internal_error", ex);
        SyncErrorResponse body = new SyncErrorResponse(
                SyncErrorResponse.SyncErrorCode.SYNC_INTERNAL_ERROR,
                "Erro interno.",
                null,
                false,
                SyncEnvelope.FullSyncRequiredReason.NONE,
                true,
                SyncErrorResponse.SyncRecoveryAction.RETRY,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
