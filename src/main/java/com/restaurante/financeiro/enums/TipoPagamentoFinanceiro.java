package com.restaurante.financeiro.enums;

/**
 * Tipo de pagamento no contexto financeiro
 * 
 * SEPARAÇÃO DE CONCEITOS:
 * - PRE_PAGO: Cliente paga antecipadamente (recarga de fundo)
 * - POS_PAGO: Cliente paga depois (autorização gerencial)
 */
public enum TipoPagamentoFinanceiro {
    
    /**
     * Pré-pago: Recarga de fundo de consumo
     * Cliente carrega saldo antes de consumir
     */
    PRE_PAGO("Pré-Pago (Fundo de Consumo)"),
    
    /**
     * Pós-pago: Pagamento posterior
     * Requer autorização GERENTE/ADMIN
     */
    POS_PAGO("Pós-Pago (Pagamento Posterior)");
    
    private final String descricao;
    
    TipoPagamentoFinanceiro(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    /**
     * Verifica se requer autorização especial
     */
    public boolean requerAutorizacao() {
        return this == POS_PAGO;
    }
    
    /**
     * Verifica se envolve Fundo de Consumo
     */
    public boolean usaFundoConsumo() {
        return this == PRE_PAGO;
    }
}
