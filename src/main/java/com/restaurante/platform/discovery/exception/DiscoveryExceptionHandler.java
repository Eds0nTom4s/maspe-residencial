package com.restaurante.platform.discovery.exception;

import com.restaurante.platform.discovery.domain.DiscoveryError;
import com.restaurante.platform.discovery.dto.DiscoveryErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.restaurante.platform.discovery.controller")
@Slf4j
public class DiscoveryExceptionHandler {

    @ExceptionHandler(DiscoveryApiException.class)
    public ResponseEntity<DiscoveryErrorResponse> handleDiscovery(
            DiscoveryApiException exception, HttpServletRequest request) {
        HttpStatus status = status(exception.getReason());
        if (status.is5xxServerError()) {
            log.warn(
                    "Discovery request failed: method={}, path={}, code={}, status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getReason(),
                    status.value());
        }
        return response(status, exception.getReason(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ConversionFailedException.class})
    public ResponseEntity<DiscoveryErrorResponse> handleInvalidParameter(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                DiscoveryError.INVALID_REQUEST,
                "Parâmetro de request inválido.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DiscoveryErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error(
                "Unexpected Discovery failure: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                DiscoveryError.UNKNOWN,
                "Não foi possível concluir o Discovery.",
                request);
    }

    private ResponseEntity<DiscoveryErrorResponse> response(
            HttpStatus status,
            DiscoveryError reason,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new DiscoveryErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        reason.name(),
                        message,
                        request.getRequestURI()));
    }

    private HttpStatus status(DiscoveryError reason) {
        return switch (reason) {
            case INVALID_REQUEST, SORT_NOT_SUPPORTED -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
