package com.restaurante.exception;

/**
 * Callback com assinatura inválida (HTTP 401).
 */
public class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException(String message) {
        super(message);
    }
}

