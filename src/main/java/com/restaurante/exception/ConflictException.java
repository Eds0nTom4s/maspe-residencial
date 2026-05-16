package com.restaurante.exception;

/**
 * Exceção para conflitos (HTTP 409), tipicamente idempotência/concorrrência.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

