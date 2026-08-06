package com.restaurante.android.api.error;

import java.util.Objects;

public record AndroidPublicErrorEnvelope(AndroidPublicError error) {

    public AndroidPublicErrorEnvelope {
        error = Objects.requireNonNull(error, "Error is required.");
    }
}
