package com.restaurante.android.foundation.identity;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Canonical UUID v4 rules for the CONSUMA AQUI public API. */
public final class PublicIdSupport {

    private static final Pattern LOWERCASE_UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private PublicIdSupport() {
    }

    public static UUID generate() {
        return UUID.randomUUID();
    }

    public static UUID parseCanonical(String value) {
        if (value == null || !LOWERCASE_UUID_V4.matcher(value).matches()) {
            throw new IllegalArgumentException("Public ID must be a lowercase canonical UUID v4.");
        }
        return UUID.fromString(value);
    }

    public static String format(UUID value) {
        UUID required = Objects.requireNonNull(value, "Public ID is required.");
        if (required.version() != 4 || required.variant() != 2) {
            throw new IllegalArgumentException("Public ID must be a UUID v4.");
        }
        return required.toString();
    }
}
