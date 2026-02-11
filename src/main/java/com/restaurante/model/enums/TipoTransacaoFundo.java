package com.restaurante.model.enums;

/**
 * Tipo de transação em Fundo de Consumo
 * 
 * CREDITO: Recarga de saldo
 * DEBITO: Pagamento de pedido
 * ESTORNO: Devolução por cancelamento
 */
public enum TipoTransacaoFundo {
    CREDITO("Crédito (Recarga)"),
    DEBITO("Débito (Pedido)"),
    ESTORNO("Estorno (Cancelamento)");

    private final String descricao;

    TipoTransacaoFundo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Verifica se aumenta saldo
     */
    public boolean aumentaSaldo() {
        return this == CREDITO || this == ESTORNO;
    }

    /**
     * Verifica se diminui saldo
     */
    public boolean diminuiSaldo() {
        return this == DEBITO;
    }
}
