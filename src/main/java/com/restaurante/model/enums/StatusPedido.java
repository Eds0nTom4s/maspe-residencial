package com.restaurante.model.enums;

/**
 * Enum que representa os possíveis estados de um pedido
 * 
 * PENDENTE - Pedido criado mas ainda não recebido pela cozinha/bar
 * RECEBIDO - Pedido confirmado e em preparação
 * EM_PREPARO - Pedido sendo preparado
 * PRONTO - Pedido pronto para ser servido
 * ENTREGUE - Pedido entregue ao cliente
 * CANCELADO - Pedido cancelado
 */
public enum StatusPedido {
    PENDENTE("Pendente"),
    RECEBIDO("Recebido"),
    EM_PREPARO("Em Preparo"),
    PRONTO("Pronto"),
    ENTREGUE("Entregue"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
