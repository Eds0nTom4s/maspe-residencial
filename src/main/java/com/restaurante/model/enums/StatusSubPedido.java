package com.restaurante.model.enums;

/**
 * Estados oficiais do SubPedido
 * 
 * MÁQUINA DE ESTADOS - BACKEND PURO
 * 
 * Estados:
 * - CRIADO: Registrado, aguardando confirmação
 * - PENDENTE: Confirmado, aguardando início de preparação
 * - EM_PREPARACAO: Sendo preparado pela cozinha
 * - PRONTO: Preparado, aguardando entrega
 * - ENTREGUE: Entregue ao cliente (TERMINAL)
 * - CANCELADO: Cancelado com motivo (TERMINAL)
 * 
 * Transições válidas:
 * CRIADO → PENDENTE
 * CRIADO → CANCELADO (apenas GERENTE/ADMIN)
 * 
 * PENDENTE → EM_PREPARACAO (COZINHA assume)
 * PENDENTE → CANCELADO (apenas GERENTE/ADMIN)
 * 
 * EM_PREPARACAO → PRONTO (COZINHA finaliza)
 * EM_PREPARACAO → CANCELADO (apenas GERENTE/ADMIN com motivo)
 * 
 * PRONTO → ENTREGUE (ATENDENTE confirma)
 * 
 * Estados TERMINAIS (não aceitam transições):
 * - ENTREGUE
 * - CANCELADO
 */
public enum StatusSubPedido {
    CRIADO("Criado"),
    PENDENTE("Pendente"),
    EM_PREPARACAO("Em Preparação"),
    PRONTO("Pronto"),
    ENTREGUE("Entregue"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusSubPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Verifica se o estado é terminal (não aceita transições)
     */
    public boolean isTerminal() {
        return this == ENTREGUE || this == CANCELADO;
    }

    /**
     * Verifica se pode transicionar para novo estado
     * NÃO valida permissões - apenas transições válidas
     */
    public boolean podeTransicionarPara(StatusSubPedido novoStatus) {
        if (this == novoStatus) {
            return true; // Idempotência
        }
        
        if (this.isTerminal()) {
            return false; // Estados terminais não aceitam transições
        }
        
        return switch (this) {
            case CRIADO -> novoStatus == PENDENTE || novoStatus == CANCELADO;
            case PENDENTE -> novoStatus == EM_PREPARACAO || novoStatus == CANCELADO;
            case EM_PREPARACAO -> novoStatus == PRONTO || novoStatus == CANCELADO;
            case PRONTO -> novoStatus == ENTREGUE;
            default -> false;
        };
    }
}
