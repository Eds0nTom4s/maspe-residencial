package com.restaurante.model.enums;

/**
 * Resultado possível da tentativa de expiração automática de uma sessão de consumo.
 *
 * <p>Usado por {@code SessaoConsumoService#expirarComSeguranca} para reportar
 * ao scheduler o motivo exato de cada decisão, permitindo logs e métricas precisas.
 */
public enum ExpiracaoSessaoResultado {

    /**
     * Sessão expirada com sucesso.
     */
    EXPIRADA("Sessão expirada por inatividade automática"),

    /**
     * Sessão ignorada — já não estava ABERTA ao ser processada.
     */
    IGNORADA_STATUS_NAO_ABERTA("Sessão ignorada: status não era ABERTA"),

    /**
     * Bloqueado — atividade recente registada dentro da janela de inatividade.
     */
    BLOQUEADA_ATIVIDADE_RECENTE("Bloqueado: atividade recente registada"),

    /**
     * Bloqueado — fundo de consumo com saldo positivo; encerramento bloqueado para proteção financeira.
     */
    BLOQUEADA_SALDO_POSITIVO("Bloqueado: FundoConsumo com saldo positivo"),

    /**
     * Bloqueado — existem pedidos em estado operacional não-terminal (CRIADO ou EM_ANDAMENTO).
     */
    BLOQUEADA_PEDIDO_PENDENTE("Bloqueado: pedido(s) em andamento"),

    /**
     * Bloqueado — existem pagamentos com status PENDENTE vinculados ao fundo desta sessão.
     */
    BLOQUEADA_PAGAMENTO_PENDENTE("Bloqueado: pagamento(s) pendente(s)"),

    /**
     * Erro inesperado durante o processamento da sessão.
     */
    ERRO("Erro inesperado durante expiração");

    private final String descricao;

    ExpiracaoSessaoResultado(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
