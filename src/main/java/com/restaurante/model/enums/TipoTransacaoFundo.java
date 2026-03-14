package com.restaurante.model.enums;

/**
 * Tipo de transação em Fundo de Consumo
 * 
 * CREDITO: Recarga de saldo
 * DEBITO: Pagamento de pedido
 * ESTORNO: Devolução por cancelamento
 * AJUSTE: Correção excecional administrativa
 */
public enum TipoTransacaoFundo {
    CREDITO("Crédito (Recarga)"),
    DEBITO("Débito (Pedido)"),
    ESTORNO("Estorno (Cancelamento)"),
    AJUSTE("Ajuste (Administrativo)");

    private final String descricao;

    TipoTransacaoFundo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Verifica se aumenta saldo (Crédito ou Estorno)
     * Ajuste pode ser positivo ou negativo, portanto não é definido universalmente aqui.
     */
    public boolean aumentaSaldo() {
        return this == CREDITO || this == ESTORNO;
    }

    /**
     * Verifica se diminui saldo (Débito)
     */
    public boolean diminuiSaldo() {
        return this == DEBITO;
    }
}
