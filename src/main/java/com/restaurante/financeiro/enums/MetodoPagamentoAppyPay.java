package com.restaurante.financeiro.enums;

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
}
