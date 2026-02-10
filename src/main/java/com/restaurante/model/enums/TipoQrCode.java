package com.restaurante.model.enums;

/**
 * Tipos de QR Code no sistema
 */
public enum TipoQrCode {
    /**
     * QR Code fixo de mesa/unidade de consumo
     * Validade: 24 horas (renovação automática)
     * Uso: Múltiplo (cliente acessa cardápio)
     */
    MESA,

    /**
     * QR Code temporário para confirmação de entrega
     * Validade: 30 minutos
     * Uso: Único (garçom confirma entrega)
     */
    ENTREGA,

    /**
     * QR Code para pagamento
     * Validade: 1 hora
     * Uso: Único (cliente finaliza pagamento)
     */
    PAGAMENTO;

    /**
     * Retorna validade em minutos conforme o tipo
     */
    public long getValidadeMinutos() {
        return switch (this) {
            case MESA -> 1440; // 24 horas
            case ENTREGA -> 30; // 30 minutos
            case PAGAMENTO -> 60; // 1 hora
        };
    }

    /**
     * Verifica se permite múltiplos usos
     */
    public boolean isUsoMultiplo() {
        return this == MESA;
    }

    public String getDescricao() {
        return switch (this) {
            case MESA -> "QR Code de Mesa/Unidade de Consumo";
            case ENTREGA -> "QR Code de Confirmação de Entrega";
            case PAGAMENTO -> "QR Code de Pagamento";
        };
    }
}
