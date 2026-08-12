package com.restaurante.android.options;

/** Raised before a public Product is emitted when legacy or invalid options cannot satisfy RC.2. */
public class ProductOptionsNotProjectableException extends IllegalStateException {
    private final CanonicalProductOptionsCompatibility compatibility;

    public ProductOptionsNotProjectableException(CanonicalProductOptionsCompatibility compatibility, String message) {
        super(message);
        this.compatibility = compatibility;
    }

    public CanonicalProductOptionsCompatibility getCompatibility() {
        return compatibility;
    }
}
