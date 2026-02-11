package com.restaurante.model.enums;

/**
 * Estados oficiais do Pedido (agregado de SubPedidos)
 * 
 * MÁQUINA DE ESTADOS - BACKEND PURO
 * 
 * Estados:
 * - CRIADO: Pedido registrado
 * - EM_ANDAMENTO: Pelo menos um SubPedido em execução
 * - FINALIZADO: Todos SubPedidos ENTREGUE (TERMINAL)
 * - CANCELADO: Pedido cancelado (TERMINAL)
 * 
 * Status calculado automaticamente baseado nos SubPedidos:
 * - Todos SubPedidos CRIADO/PENDENTE → CRIADO
 * - Qualquer SubPedido EM_PREPARACAO/PRONTO → EM_ANDAMENTO
 * - Todos SubPedidos ENTREGUE → FINALIZADO
 * - Todos SubPedidos CANCELADO → CANCELADO
 */
public enum StatusPedido {
    CRIADO("Criado"),
    EM_ANDAMENTO("Em Andamento"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Verifica se o estado é terminal
     */
    public boolean isTerminal() {
        return this == FINALIZADO || this == CANCELADO;
    }
}
