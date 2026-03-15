package com.restaurante.exception;

import java.math.BigDecimal;

/**
 * Exceção lançada quando limite de pós-pago é excedido
 */
public class LimitePosPagoExcedidoException extends BusinessException {

    public LimitePosPagoExcedidoException(BigDecimal totalAberto, BigDecimal limite) {
        super(String.format(
            "Limite de pós-pago excedido. Total aberto: %s, Limite: %s",
            com.restaurante.util.MoneyFormatter.format(totalAberto),
            com.restaurante.util.MoneyFormatter.format(limite)
        ));
    }

    public LimitePosPagoExcedidoException(String mensagem) {
        super(mensagem);
    }
}
