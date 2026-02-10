package com.restaurante.model.enums;

/**
 * Enum que representa os estados de uma Unidade de Consumo
 * 
 * Unidade de Consumo pode ser:
 * - Mesa física de restaurante
 * - Quarto de hotel
 * - Área de evento
 * - Espaço virtual
 * 
 * DISPONIVEL - Unidade livre e disponível para uso
 * OCUPADA - Unidade está em uso por cliente(s)
 * AGUARDANDO_PAGAMENTO - Consumo finalizado, aguardando pagamento
 * FINALIZADA - Pagamento concluído, pronta para ser liberada
 */
public enum StatusUnidadeConsumo {
    DISPONIVEL("Disponível"),
    OCUPADA("Ocupada"),
    AGUARDANDO_PAGAMENTO("Aguardando Pagamento"),
    FINALIZADA("Finalizada");

    private final String descricao;

    StatusUnidadeConsumo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
