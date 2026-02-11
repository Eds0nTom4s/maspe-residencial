package com.restaurante.exception;

import java.math.BigDecimal;

/**
 * Exceção lançada quando saldo no Fundo de Consumo é insuficiente
 */
public class SaldoInsuficienteException extends BusinessException {

    public SaldoInsuficienteException(BigDecimal saldoAtual, BigDecimal valorNecessario) {
        super(String.format(
            "Saldo insuficiente. Saldo atual: R$ %.2f, Valor necessário: R$ %.2f",
            saldoAtual,
            valorNecessario
        ));
    }

    public SaldoInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
