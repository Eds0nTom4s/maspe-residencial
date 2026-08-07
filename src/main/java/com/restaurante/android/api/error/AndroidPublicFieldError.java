package com.restaurante.android.api.error;

import java.util.Objects;

public record AndroidPublicFieldError(String field, String code, String message) {

    public AndroidPublicFieldError {
        field = Objects.requireNonNull(field, "Field path is required.");
        code = Objects.requireNonNull(code, "Field error code is required.");
        message = Objects.requireNonNull(message, "Field error message is required.");
    }
}
