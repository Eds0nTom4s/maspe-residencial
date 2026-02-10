package com.restaurante.model.enums;

/**
 * Enum que representa os tipos de Cozinha (recurso operacional)
 * 
 * Define o tipo de preparação e recurso disponível
 */
public enum TipoCozinha {
    CENTRAL("Cozinha Central"),
    BAR_PREP("Preparação de Bar"),
    CONFEITARIA("Confeitaria"),
    GRILL("Grill/Churrasqueira"),
    PIZZARIA("Pizzaria"),
    SUSHI("Sushi Bar"),
    ESPECIAL("Especializada");

    private final String descricao;

    TipoCozinha(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
