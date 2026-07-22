package com.restaurante.platform.discovery.exception;

import com.restaurante.platform.discovery.domain.DiscoveryError;

public class DiscoveryValidationException extends RuntimeException {

    private final DiscoveryError reason;

    public DiscoveryValidationException(String message) {
        this(DiscoveryError.INVALID_REQUEST, message);
    }

    public DiscoveryValidationException(DiscoveryError reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DiscoveryError getReason() {
        return reason;
    }
}
