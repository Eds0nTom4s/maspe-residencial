package com.restaurante.exception;

public class DeviceForbiddenException extends RuntimeException {
    public DeviceForbiddenException(String message) {
        super(message);
    }
}

