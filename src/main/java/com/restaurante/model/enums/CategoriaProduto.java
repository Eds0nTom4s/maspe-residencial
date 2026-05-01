package com.restaurante.model.enums;

/**
 * Enum que representa as categorias de produtos
 */
public enum CategoriaProduto {
    // ── Restaurante (mantidos para compatibilidade) ───────────────────────────
    ENTRADA("Entrada"),
    PRATO_PRINCIPAL("Prato Principal"),
    ACOMPANHAMENTO("Acompanhamento"),
    SOBREMESA("Sobremesa"),
    BEBIDA_ALCOOLICA("Bebida Alcoólica"),
    BEBIDA_NAO_ALCOOLICA("Bebida Não Alcoólica"),
    LANCHE("Lanche"),
    PIZZA("Pizza"),
    OUTROS("Outros"),

    // ── Loja do Sócio (Sagrada Esperança) ────────────────────────────────────
    VESTUARIO("Vestuário"),
    EQUIPAMENTO_DESPORTIVO("Equipamento Desportivo"),
    ACESSORIO("Acessório"),
    COLECCIONAVEL("Coleccionável");

    private final String descricao;

    CategoriaProduto(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
