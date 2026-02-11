package com.restaurante.exception;

/**
 * Exceção lançada quando cliente tenta criar pedido pós-pago
 * 
 * REGRA DE NEGÓCIO:
 * - Apenas GERENTE e ADMIN podem autorizar pós-pago
 * - Cliente DEVE usar pré-pago (Fundo de Consumo)
 */
public class PosPagoNaoPermitidoException extends BusinessException {

    public PosPagoNaoPermitidoException() {
        super("Cliente não pode criar pedido pós-pago. Use Fundo de Consumo (pré-pago) ou solicite autorização de GERENTE/ADMIN.");
    }

    public PosPagoNaoPermitidoException(String mensagem) {
        super(mensagem);
    }
}
