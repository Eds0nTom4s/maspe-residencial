package com.restaurante.android.api.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public final class AndroidPublicApiException extends RuntimeException {

    private final AndroidPublicErrorCode code;
    private final HttpStatus status;
    private final boolean retryable;
    private final List<AndroidPublicFieldError> fieldErrors;

    public AndroidPublicApiException(
            AndroidPublicErrorCode code,
            HttpStatus status,
            String publicMessage,
            boolean retryable,
            List<AndroidPublicFieldError> fieldErrors) {
        super(publicMessage);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
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
}
