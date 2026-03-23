package com.restaurante.model.enums;

/**
 * Enum que representa os possíveis métodos de pagamento
 * 
 * Estrutura preparada para integração futura com gateways de pagamento
 */
public enum MetodoPagamento {
    CASH("Cash / Dinheiro Físico"),
    TPA("TPA / Cartão Multicaixa"),
    DIGITAL("Pagamento Digital (App/Carteira)");

    private final String descricao;

    MetodoPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
