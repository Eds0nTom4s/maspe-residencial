package com.restaurante.exception;

/**
 * Exceção lançada quando usuário não tem permissão para executar ação
 * 
 * Exemplos:
 * - CLIENTE tentando marcar SubPedido como PRONTO
 * - COZINHA tentando marcar como ENTREGUE
 * - ATENDENTE tentando cancelar SubPedido
 */
public class PermissaoNegadaException extends BusinessException {
    
    public PermissaoNegadaException(String message) {
        super(message);
    }
    
    public PermissaoNegadaException(String role, String acao) {
        super(String.format("Permissão negada: role %s não pode executar ação '%s'", 
            role, acao));
    }
}
