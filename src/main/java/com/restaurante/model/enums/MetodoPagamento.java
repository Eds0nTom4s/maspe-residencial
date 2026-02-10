package com.restaurante.model.enums;

/**
 * Enum que representa os possíveis métodos de pagamento
 * 
 * Estrutura preparada para integração futura com gateways de pagamento
 */
public enum MetodoPagamento {
    DINHEIRO("Dinheiro"),
    CARTAO_CREDITO("Cartão de Crédito"),
    CARTAO_DEBITO("Cartão de Débito"),
    PIX("PIX"),
    VALE_REFEICAO("Vale Refeição"),
    DIGITAL("Pagamento Digital");

    private final String descricao;

    MetodoPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
