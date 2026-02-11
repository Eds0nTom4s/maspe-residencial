package com.restaurante.model.enums;

/**
 * Estado financeiro do pedido
 * 
 * SEPARAÇÃO DE CONCEITOS:
 * - Estado OPERACIONAL: ciclo de vida do pedido (CRIADO → ENTREGUE)
 * - Estado FINANCEIRO: situação de pagamento (NAO_PAGO → PAGO)
 * 
 * INDEPENDENTES:
 * - Pedido pode estar ENTREGUE e NAO_PAGO
 * - Pedido pode estar PAGO e ainda PENDENTE
 * 
 * Estados:
 * - NAO_PAGO: Aguardando pagamento (pós-pago)
 * - PAGO: Pagamento confirmado (pré-pago ou fundo)
 * - ESTORNADO: Pagamento estornado (cancelamento)
 */
public enum StatusFinanceiroPedido {
    NAO_PAGO("Não Pago"),
    PAGO("Pago"),
    ESTORNADO("Estornado");

    private final String descricao;

    StatusFinanceiroPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Verifica se pagamento está confirmado
     */
    public boolean isPago() {
        return this == PAGO;
    }

    /**
     * Verifica se pode estornar
     */
    public boolean podeEstornar() {
        return this == PAGO;
    }
}
