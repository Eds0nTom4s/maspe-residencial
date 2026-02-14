package com.restaurante.financeiro.enums;

/**
 * Status do pagamento no gateway AppyPay
 * 
 * Ciclo de vida:
 * PENDENTE → CONFIRMADO (sucesso)
 * PENDENTE → FALHOU (erro)
 * CONFIRMADO → ESTORNADO (cancelamento)
 */
public enum StatusPagamentoGateway {
    
    /**
     * Aguardando confirmação
     * - GPO: Estado transitório (milissegundos)
     * - REF: Aguarda pagamento do cliente
     */
    PENDENTE("Aguardando confirmação"),
    
    /**
     * Pagamento confirmado pelo gateway
     * Estado final (sucesso)
     */
    CONFIRMADO("Pagamento confirmado"),
    
    /**
     * Pagamento falhou
     * Estado final (erro)
     */
    FALHOU("Pagamento falhou"),
    
    /**
     * Pagamento estornado
     * Estado final (cancelamento)
     */
    ESTORNADO("Pagamento estornado");
    
    private final String descricao;
    
    StatusPagamentoGateway(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    /**
     * Verifica se é estado terminal (não muda mais)
     */
    public boolean isTerminal() {
        return this == CONFIRMADO || this == FALHOU || this == ESTORNADO;
    }
    
    /**
     * Verifica se pode ser estornado
     */
    public boolean podeEstornar() {
        return this == CONFIRMADO;
    }
}
