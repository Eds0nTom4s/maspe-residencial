package com.restaurante.model.enums;

/**
 * Enum que representa os estados de um SubPedido
 * 
 * SubPedido é a unidade operacional de execução
 * Estados seguem fluxo linear e são controlados por roles específicas
 * 
 * FLUXO:
 * PENDENTE → EM_PREPARACAO → PRONTO → ENTREGUE
 * 
 * REGRAS:
 * - Cliente NUNCA altera estado
 * - Cozinha altera até PRONTO
 * - Garçom/Balcão confirmam ENTREGA
 */
public enum StatusSubPedido {
    PENDENTE("Pendente"),
    RECEBIDO("Recebido"),           // Assumido pela cozinha
    EM_PREPARACAO("Em Preparação"),  // Iniciado preparo
    PRONTO("Pronto"),                // Pronto para entrega (gera ticket)
    ENTREGUE("Entregue"),            // Confirmado por garçom
    CANCELADO("Cancelado");          // Cancelado pelo balcão

    private final String descricao;

    StatusSubPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Valida se é possível avançar para o próximo estado
     */
    public boolean podeAvancarPara(StatusSubPedido novoStatus) {
        return switch (this) {
            case PENDENTE -> novoStatus == RECEBIDO || novoStatus == CANCELADO;
            case RECEBIDO -> novoStatus == EM_PREPARACAO || novoStatus == CANCELADO;
            case EM_PREPARACAO -> novoStatus == PRONTO || novoStatus == CANCELADO;
            case PRONTO -> novoStatus == ENTREGUE;
            case ENTREGUE, CANCELADO -> false; // Estados finais
        };
    }
}
