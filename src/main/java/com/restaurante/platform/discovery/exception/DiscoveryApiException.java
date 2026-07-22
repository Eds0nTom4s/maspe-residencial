package com.restaurante.platform.discovery.exception;

import com.restaurante.platform.discovery.domain.DiscoveryError;

public class DiscoveryApiException extends RuntimeException {

    private final DiscoveryError reason;

    public DiscoveryApiException(DiscoveryError reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DiscoveryError getReason() {
        return reason;
    }
}
