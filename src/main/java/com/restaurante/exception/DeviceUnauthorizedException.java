package com.restaurante.exception;

public class DeviceUnauthorizedException extends RuntimeException {
    public DeviceUnauthorizedException(String message) {
        super(message);
    }
}

