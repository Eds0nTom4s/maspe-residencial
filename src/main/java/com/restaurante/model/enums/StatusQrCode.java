package com.restaurante.model.enums;

/**
 * Status do QR Code
 */
public enum StatusQrCode {
    /**
     * QR Code ativo e válido para uso
     */
    ATIVO,

    /**
     * QR Code já utilizado (apenas para uso único)
     */
    USADO,

    /**
     * QR Code expirado (ultrapassou validade)
     */
    EXPIRADO,

    /**
     * QR Code cancelado manualmente
     */
    CANCELADO;

    /**
     * Verifica se o status permite uso
     */
    public boolean isUsavel() {
        return this == ATIVO;
    }

    public String getDescricao() {
        return switch (this) {
            case ATIVO -> "Ativo e válido";
            case USADO -> "Já utilizado";
            case EXPIRADO -> "Expirado";
            case CANCELADO -> "Cancelado";
        };
    }
}
