package com.restaurante.exception;

public class TurnoObrigatorioException extends RuntimeException {

    public static final String CODE = "TURNO_ABERTO_OBRIGATORIO";

    public TurnoObrigatorioException(String message) {
        super(message);
    }
}
