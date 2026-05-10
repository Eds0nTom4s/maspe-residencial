package com.restaurante.financeiro.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Métodos de pagamento suportados pela AppyPay
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 * 
 * GPO: Pagamento instantâneo via aplicativo AppyPay
 * REF: Pagamento por referência bancária (aguarda confirmação)
 */
public enum MetodoPagamentoAppyPay {
    
    /**
     * GPO - Pagamento imediato via AppyPay
     * Confirmação instantânea
     */
    GPO("GPO", "Pagamento AppyPay", true),
    
    /**
     * REF - Referência bancária
     * Gera entidade + referência para pagamento multicaixa
     * Confirmação via callback
     */
    REF("REF", "Referência Bancária", false);
    
    private final String codigo;
    private final String descricao;
    private final boolean instantaneo;
    
    MetodoPagamentoAppyPay(String codigo, String descricao, boolean instantaneo) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.instantaneo = instantaneo;
    }
    
    public String getCodigo() {
        return codigo;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    public boolean isInstantaneo() {
        return instantaneo;
    }
    
    /**
     * Verifica se requer callback para confirmação
     */
    public boolean requerCallback() {
        return !instantaneo;
    }

    @JsonCreator
    public static MetodoPagamentoAppyPay from(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        String normalizado = valor.trim().toUpperCase();
        return switch (normalizado) {
            case "GPO", "MULTICAIXA_EXPRESS", "MULTICAIXA-EXPRESS", "MULTICAIXA EXPRESS" -> GPO;
            case "REF", "REFERENCIA", "REFERÊNCIA", "REFERENCIA_BANCARIA", "REFERÊNCIA_BANCÁRIA" -> REF;
            default -> throw new IllegalArgumentException(
                    "Método de pagamento inválido: " + valor + ". Valores aceites: GPO, MULTICAIXA_EXPRESS, REF, REFERENCIA");
        };
    }
}
