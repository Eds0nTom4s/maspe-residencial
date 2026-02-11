package com.restaurante.exception;

/**
 * Exceção lançada quando uma transição de estado é inválida
 * 
 * Exemplos:
 * - Tentar marcar ENTREGUE quando está PENDENTE
 * - Tentar alterar estado terminal (ENTREGUE, CANCELADO)
 */
public class TransicaoInvalidaException extends BusinessException {
    
    public TransicaoInvalidaException(String message) {
        super(message);
    }
    
    public TransicaoInvalidaException(String estadoAtual, String estadoDestino) {
        super(String.format("Transição inválida: não é possível ir de %s para %s", 
            estadoAtual, estadoDestino));
    }
    
    public TransicaoInvalidaException(String estadoAtual, String estadoDestino, String motivo) {
        super(String.format("Transição inválida de %s para %s: %s", 
            estadoAtual, estadoDestino, motivo));
    }
}
