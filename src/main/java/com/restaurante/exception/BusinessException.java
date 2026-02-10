package com.restaurante.exception;

/**
 * Exceção lançada quando uma operação de negócio é inválida
 */
public class BusinessException extends RuntimeException {
    
    public BusinessException(String message) {
        super(message);
    }
}
