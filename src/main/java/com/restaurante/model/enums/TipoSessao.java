package com.restaurante.model.enums;

/**
 * Define o tipo de operação financeira de uma Sessão de Consumo.
 * 
 * PRE_PAGO: O consumo só é permitido se houver saldo prévio carregado no Fundo de Consumo.
 *            O saldo nunca pode ficar negativo.
 * POS_PAGO: O consumo é registado e o pagamento ocorre ao encerrar a sessão.
 *            O saldo do Fundo de Consumo pode tornar-se negativo durante a operação.
 */
public enum TipoSessao {
    PRE_PAGO("Pré-Pago"),
    POS_PAGO("Pós-Pago");

    private final String descricao;

    TipoSessao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
