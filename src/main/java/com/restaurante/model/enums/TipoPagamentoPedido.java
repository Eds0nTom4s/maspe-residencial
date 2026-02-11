package com.restaurante.model.enums;

/**
 * Tipo de pagamento do pedido
 * 
 * REGRAS CRÍTICAS:
 * 
 * PRE_PAGO:
 * - Cliente paga ANTES de consumir
 * - Débito automático de Fundo de Consumo
 * - StatusFinanceiro = PAGO imediatamente
 * 
 * POS_PAGO:
 * - Cliente paga DEPOIS de consumir
 * - Apenas GERENTE/ADMIN podem autorizar
 * - StatusFinanceiro = NAO_PAGO até pagamento presencial
 * - Cliente NÃO pode criar pós-pago sozinho
 */
public enum TipoPagamentoPedido {
    PRE_PAGO("Pré-Pago (Fundo de Consumo)"),
    POS_PAGO("Pós-Pago (Balcão)");

    private final String descricao;

    TipoPagamentoPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Verifica se é pré-pago
     */
    public boolean isPrePago() {
        return this == PRE_PAGO;
    }

    /**
     * Verifica se é pós-pago
     */
    public boolean isPosPago() {
        return this == POS_PAGO;
    }
}
