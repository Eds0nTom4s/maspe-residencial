package com.restaurante.android.api.error;

import com.restaurante.android.api.AndroidPublicApiController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = AndroidPublicApiController.class)
public class AndroidPublicApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AndroidPublicApiExceptionHandler.class);
    private final AndroidPublicTraceIdResolver traceIds = new AndroidPublicTraceIdResolver();

    public AndroidPublicApiExceptionHandler() {
    }

    @ExceptionHandler(AndroidPublicApiException.class)
    public ResponseEntity<AndroidPublicErrorEnvelope> handlePublic(
            AndroidPublicApiException exception, HttpServletRequest request) {
        return response(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                exception.isRetryable(),
                exception.getFieldErrors(),
                traceIds.resolve(request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AndroidPublicErrorEnvelope> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<AndroidPublicFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        return invalid(fields, traceIds.resolve(request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<AndroidPublicErrorEnvelope> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<AndroidPublicFieldError> fields = exception.getConstraintViolations().stream()
                .map(violation -> new AndroidPublicFieldError(
                        jsonPointer(violation.getPropertyPath().toString()),
                        "INVALID_VALUE",
                        violation.getMessage()))
                .toList();
        return invalid(fields, traceIds.resolve(request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AndroidPublicErrorEnvelope> handleUnreadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return invalid(List.of(), traceIds.resolve(request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AndroidPublicErrorEnvelope> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String traceId = traceIds.resolve(request);
        log.error("Unexpected Android public API failure traceId={}", traceId, exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                AndroidPublicErrorCode.INTERNAL_ERROR,
                "Não foi possível processar o pedido.",
                false,
                List.of(),
                traceId);
    }

    private ResponseEntity<AndroidPublicErrorEnvelope> invalid(
            List<AndroidPublicFieldError> fields, String traceId) {
        return response(
                HttpStatus.BAD_REQUEST,
                AndroidPublicErrorCode.INVALID_REQUEST,
                "O pedido contém dados inválidos.",
                false,
                fields,
                traceId);
    }

    private ResponseEntity<AndroidPublicErrorEnvelope> response(
            HttpStatus status,
            AndroidPublicErrorCode code,
            String message,
            boolean retryable,
            List<AndroidPublicFieldError> fields,
            String traceId) {
        AndroidPublicError error = new AndroidPublicError(
                code.name(), message, retryable, fields, traceId);
        return ResponseEntity.status(status).body(new AndroidPublicErrorEnvelope(error));
    }

    private AndroidPublicFieldError fieldError(FieldError fieldError) {
        String code = fieldError.getCode() == null ? "INVALID_VALUE" : fieldError.getCode();
        String message = fieldError.getDefaultMessage() == null
                ? "Valor inválido."
                : fieldError.getDefaultMessage();
        return new AndroidPublicFieldError(jsonPointer(fieldError.getField()), code, message);
    }

    private String jsonPointer(String field) {
        return "/" + field.replace("~", "~0").replace("/", "~1").replace('.', '/');
    }
}
