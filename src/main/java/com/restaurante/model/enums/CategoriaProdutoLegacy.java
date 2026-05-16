package com.restaurante.model.enums;

/**
 * Enum legado que representa as categorias de produtos.
 *
 * IMPORTANTE:
 * - Este enum existe por compatibilidade com o domínio anterior (single-tenant).
 * - A partir do Prompt 5, a categoria tenant-owned passa a ser a entidade {@code CategoriaProduto}.
 * - Migração completa enum -> FK será feita incrementalmente.
 */
public enum CategoriaProdutoLegacy {
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

    CategoriaProdutoLegacy(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

