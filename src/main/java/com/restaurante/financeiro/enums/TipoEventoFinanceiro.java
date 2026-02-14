package com.restaurante.financeiro.enums;

/**
 * Tipos de eventos financeiros para auditoria
 * 
 * Registra todas as ações críticas do módulo financeiro
 */
public enum TipoEventoFinanceiro {
    
    /**
     * Criação de pagamento
     */
    PAGAMENTO_CRIADO("Pagamento criado"),
    
    /**
     * Confirmação de pagamento
     */
    CONFIRMACAO_PAGAMENTO("Pagamento confirmado"),
    
    /**
     * Callback recebido do gateway
     */
    CALLBACK_RECEBIDO("Callback do gateway recebido"),
    
    /**
     * Autorização de pós-pago
     */
    AUTORIZACAO_POS_PAGO("Autorização pós-pago concedida"),
    
    /**
     * Negação de pós-pago
     */
    NEGACAO_POS_PAGO("Autorização pós-pago negada"),
    
    /**
     * Estorno manual
     */
    ESTORNO_MANUAL("Estorno manual realizado"),
    
    /**
     * Estorno automático (cancelamento)
     */
    ESTORNO_AUTOMATICO("Estorno automático realizado"),
    
    /**
     * Política pós-pago ativada
     */
    POLITICA_POS_PAGO_ATIVADA("Pós-pago ativado globalmente"),
    
    /**
     * Política pós-pago desativada
     */
    POLITICA_POS_PAGO_DESATIVADA("Pós-pago desativado globalmente"),
    
    /**
     * Recarga de fundo
     */
    RECARGA_FUNDO("Recarga de fundo de consumo"),
    
    /**
     * Falha de pagamento
     */
    PAGAMENTO_FALHOU("Pagamento falhou");
    
    private final String descricao;
    
    TipoEventoFinanceiro(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    /**
     * Verifica se evento requer motivo obrigatório
     */
    public boolean requerMotivo() {
        return this == ESTORNO_MANUAL || 
               this == NEGACAO_POS_PAGO ||
               this == PAGAMENTO_FALHOU;
    }
}
