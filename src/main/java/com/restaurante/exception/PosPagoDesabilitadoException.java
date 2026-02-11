package com.restaurante.exception;

/**
 * Exceção lançada quando pós-pago está desabilitado globalmente
 */
public class PosPagoDesabilitadoException extends BusinessException {

    public PosPagoDesabilitadoException() {
        super("Consumo pós-pago temporariamente desabilitado. Contacte o administrador.");
    }

    public PosPagoDesabilitadoException(String mensagem) {
        super(mensagem);
    }
}
