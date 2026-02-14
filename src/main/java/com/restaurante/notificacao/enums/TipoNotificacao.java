package com.restaurante.notificacao.enums;

/**
 * Tipos de notificação suportados pelo sistema
 */
public enum TipoNotificacao {
    SMS("SMS"),
    EMAIL("E-mail"),
    PUSH("Push Notification");
    
    private final String descricao;
    
    TipoNotificacao(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
}
