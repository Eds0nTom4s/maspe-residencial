package com.restaurante.android.options;

/** Whether a product can be represented by the frozen Android Product schema. */
public enum CanonicalProductOptionsCompatibility {
    NO_OPTIONS,
    CANONICAL_OPTIONS,
    LEGACY_OPTIONS_UNMIGRATED,
    CANONICAL_OPTIONS_INVALID
}
