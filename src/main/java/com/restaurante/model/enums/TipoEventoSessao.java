package com.restaurante.model.enums;

/**
 * Tipos de eventos auditáveis no ciclo de vida de uma Sessão de Consumo.
 */
public enum TipoEventoSessao {
    SESSAO_ABERTA("Sessão Aberta"),
    MESA_VINCULADA("Mesa Vinculada"),
    MESA_DESVINCULADA("Mesa Desvinculada"),
    CLIENTE_VINCULADO("Cliente Vinculado"),
    PEDIDO_CRIADO("Pedido Criado"),
    FUNDO_RECARREGADO("Fundo Recarregado"),
    FUNDO_DEBITADO("Fundo Debitado"),
    PAGAMENTO_SOLICITADO("Aguardando Pagamento"),
    SESSAO_ENCERRADA("Sessão Encerrada"),
    OUTRO("Outro Evento");

    private final String descricao;

    TipoEventoSessao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
