package com.restaurante.financeiro.exception;

/**
 * Exceção lançada quando a assinatura do callback do gateway é inválida.
 *
 * Usada para permitir que o controller retorne HTTP 401/403 sem
 * acoplar a regra ao {@code BusinessException}, que é genérica.
 */
public class InvalidCallbackSignatureException extends RuntimeException {

    public InvalidCallbackSignatureException(String message) {
        super(message);
    }
}

