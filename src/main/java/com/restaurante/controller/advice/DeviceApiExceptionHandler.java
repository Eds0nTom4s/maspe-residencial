package com.restaurante.controller.advice;

import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.exception.DeviceForbiddenException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import com.restaurante.service.metrics.DeviceSyncMetricsService;
import org.springframework.beans.factory.ObjectProvider;
import com.restaurante.service.metrics.NoOpDeviceSyncMetricsService;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeviceApiExceptionHandler {

    private final DeviceSyncMetricsService metrics;

    public DeviceApiExceptionHandler(ObjectProvider<DeviceSyncMetricsService> metricsProvider) {
        this.metrics = metricsProvider.getIfAvailable(NoOpDeviceSyncMetricsService::new);
    }

    private boolean isDeviceApiPath(HttpServletRequest req) {
        String uri = req != null ? req.getRequestURI() : null;
        return uri != null && uri.startsWith("/device") && !uri.startsWith("/device/sync");
    }

    private void maybeRecordHeartbeat(HttpServletRequest req, String result) {
        String uri = req != null ? req.getRequestURI() : null;
        if (uri != null && uri.equals("/device/heartbeat")) {
            metrics.recordHeartbeat(result);
        }
    }

    @ExceptionHandler(DeviceUnauthorizedException.class)
    public ResponseEntity<DeviceErrorResponse> unauthorized(DeviceUnauthorizedException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) throw ex;
        maybeRecordHeartbeat(req, "UNAUTHORIZED");
        DeviceErrorResponse body = new DeviceErrorResponse(
                DeviceErrorResponse.DeviceErrorCode.DEVICE_UNAUTHORIZED,
                ex.getMessage(),
                true,
                DeviceErrorResponse.DeviceRecoveryAction.REAUTH_DEVICE,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(DeviceForbiddenException.class)
    public ResponseEntity<DeviceErrorResponse> forbidden(DeviceForbiddenException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) throw ex;
        maybeRecordHeartbeat(req, "FORBIDDEN");
        DeviceErrorResponse body = new DeviceErrorResponse(
                DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                ex.getMessage(),
                false,
                DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DeviceErrorResponse> invalidRequest(MethodArgumentNotValidException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) throw new RuntimeException(ex);
        DeviceErrorResponse body = new DeviceErrorResponse(
                DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                "Dados inválidos fornecidos.",
                true,
                DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                LocalDateTime.now(),
                Map.of("errors", ex.getBindingResult().getErrorCount())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<DeviceErrorResponse> business(BusinessException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) throw ex;
        DeviceErrorResponse body = new DeviceErrorResponse(
                DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                ex.getMessage(),
                true,
                DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
