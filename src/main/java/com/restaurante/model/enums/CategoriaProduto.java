package com.restaurante.model.enums;

/**
 * Enum que representa as categorias de produtos
 */
public enum CategoriaProduto {
    ENTRADA("Entrada"),
    PRATO_PRINCIPAL("Prato Principal"),
    ACOMPANHAMENTO("Acompanhamento"),
    SOBREMESA("Sobremesa"),
    BEBIDA_ALCOOLICA("Bebida Alcoólica"),
    BEBIDA_NAO_ALCOOLICA("Bebida Não Alcoólica"),
    LANCHE("Lanche"),
    PIZZA("Pizza"),
    OUTROS("Outros");

    private final String descricao;

    CategoriaProduto(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
