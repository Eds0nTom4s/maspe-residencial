package com.restaurante.model.enums;

/**
 * Enum que representa os tipos de Unidade de Atendimento
 * 
 * Cada tipo representa um ponto de entrada diferente no sistema
 */
public enum TipoUnidadeAtendimento {
    RESTAURANTE("Restaurante"),
    BAR("Bar"),
    CAFETERIA("Cafeteria"),
    ROOM_SERVICE("Room Service"),
    EVENTO("Evento"),
    PISCINA("Piscina"),
    LOUNGE("Lounge");

    private final String descricao;

    TipoUnidadeAtendimento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
