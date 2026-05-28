package com.restaurante.controller.advice;

import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.exception.DeviceApiException;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DeviceApiExceptionHandler {

    private final DeviceSyncMetricsService metrics;

    public DeviceApiExceptionHandler(ObjectProvider<DeviceSyncMetricsService> metricsProvider) {
        this.metrics = metricsProvider.getIfAvailable(NoOpDeviceSyncMetricsService::new);
    }

    private boolean isDeviceApiPath(HttpServletRequest req) {
        String uri = req != null ? req.getRequestURI() : null;
        return uri != null && 
               (uri.startsWith("/device") || uri.startsWith("/api/device")) && 
               !(uri.startsWith("/device/sync") || uri.startsWith("/api/device/sync"));
    }

    private void maybeRecordHeartbeat(HttpServletRequest req, String result) {
        String uri = req != null ? req.getRequestURI() : null;
        if (uri != null && (uri.equals("/device/heartbeat") || uri.equals("/api/device/heartbeat"))) {
            metrics.recordHeartbeat(result);
        }
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

    @ExceptionHandler(DeviceUnauthorizedException.class)
    public ResponseEntity<Object> unauthorized(DeviceUnauthorizedException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.UNAUTHORIZED, "Não autenticado", "DEVICE_UNAUTHORIZED");
        }
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
    public ResponseEntity<Object> forbidden(DeviceForbiddenException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.FORBIDDEN, "Acesso negado", "DEVICE_FORBIDDEN");
        }
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
    public ResponseEntity<Object> invalidRequest(MethodArgumentNotValidException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) {
            java.util.Map<String, String> errors = new java.util.HashMap<>();
            ex.getBindingResult().getAllErrors().forEach((error) -> {
                String fieldName = ((org.springframework.validation.FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
            var body = com.restaurante.exception.GlobalExceptionHandler.ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.BAD_REQUEST.value())
                    .error("Erro de validação")
                    .message("Dados inválidos fornecidos")
                    .path(req != null ? req.getRequestURI() : "")
                    .validationErrors(errors)
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
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
    public ResponseEntity<Object> business(BusinessException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) {
            return delegateToGlobal(ex, req, HttpStatus.BAD_REQUEST, "Erro de negócio", null);
        }
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

    @ExceptionHandler(DeviceApiException.class)
    public ResponseEntity<Object> deviceApi(DeviceApiException ex, HttpServletRequest req) {
        if (!isDeviceApiPath(req)) {
            return delegateToGlobal(ex, req, ex.getStatus(), "Erro na API do dispositivo", ex.getCode() != null ? ex.getCode().name() : null);
        }
        DeviceErrorResponse body = new DeviceErrorResponse(
                ex.getCode(),
                ex.getMessage(),
                ex.isRecoverable(),
                ex.getAction(),
                LocalDateTime.now(),
                ex.getDetails()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
