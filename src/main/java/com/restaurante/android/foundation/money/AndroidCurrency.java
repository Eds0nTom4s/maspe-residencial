package com.restaurante.android.foundation.money;

import java.util.Locale;

/** Closed currency policy for the Android V1 contract. */
public enum AndroidCurrency {
    AOA(2);

    private final int scale;

    AndroidCurrency(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }

    public static AndroidCurrency normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Currency code is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"AOA".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported Android V1 currency.");
        }
        return AOA;
    }
}
