package com.restaurante.android.api.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public final class AndroidPublicApiException extends RuntimeException {

    private final AndroidPublicErrorCode code;
    private final HttpStatus status;
    private final boolean retryable;
    private final List<AndroidPublicFieldError> fieldErrors;
    private final Long retryAfterSeconds;

    public AndroidPublicApiException(
            AndroidPublicErrorCode code,
            HttpStatus status,
            String publicMessage,
            boolean retryable,
            List<AndroidPublicFieldError> fieldErrors) {
        this(code, status, publicMessage, retryable, fieldErrors, null);
    }

    public AndroidPublicApiException(
            AndroidPublicErrorCode code,
            HttpStatus status,
            String publicMessage,
            boolean retryable,
            List<AndroidPublicFieldError> fieldErrors,
            Long retryAfterSeconds) {
        super(publicMessage);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public AndroidPublicErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public List<AndroidPublicFieldError> getFieldErrors() {
        return fieldErrors;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
