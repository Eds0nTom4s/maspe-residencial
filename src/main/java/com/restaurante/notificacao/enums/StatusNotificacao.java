package com.restaurante.notificacao.enums;

/**
 * Status de envio de notificação
 */
public enum StatusNotificacao {
    PENDENTE("Pendente de envio"),
    ENVIADA("Enviada com sucesso"),
    FALHA("Falha no envio"),
    ENTREGUE("Entregue ao destinatário");
    
    private final String descricao;
    
    StatusNotificacao(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
}
