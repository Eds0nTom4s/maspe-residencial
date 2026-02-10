package com.restaurante.model.enums;

/**
 * Enum que representa os tipos de Unidade de Consumo
 * 
 * Define a natureza física/virtual da unidade
 */
public enum TipoUnidadeConsumo {
    MESA_FISICA("Mesa Física"),
    QUARTO("Quarto de Hotel"),
    AREA_EVENTO("Área de Evento"),
    ESPACO_LOUNGE("Espaço Lounge"),
    VIRTUAL("Virtual/Delivery");

    private final String descricao;

    TipoUnidadeConsumo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
