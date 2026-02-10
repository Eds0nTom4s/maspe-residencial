package com.restaurante.model.enums;

/**
 * Enum que representa os possíveis status de um pagamento
 */
public enum StatusPagamento {
    PENDENTE("Pendente"),
    PROCESSANDO("Processando"),
    APROVADO("Aprovado"),
    RECUSADO("Recusado"),
    CANCELADO("Cancelado"),
    ESTORNADO("Estornado");

    private final String descricao;

    StatusPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
