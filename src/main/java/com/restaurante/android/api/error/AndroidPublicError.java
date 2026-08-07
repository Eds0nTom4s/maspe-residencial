package com.restaurante.android.api.error;

import java.util.List;
import java.util.Objects;

public record AndroidPublicError(
        String code,
        String message,
        boolean retryable,
        List<AndroidPublicFieldError> fieldErrors,
        String traceId) {

    public AndroidPublicError {
        code = Objects.requireNonNull(code, "Error code is required.");
        message = Objects.requireNonNull(message, "Public message is required.");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        traceId = Objects.requireNonNull(traceId, "Trace ID is required.");
    }
}
