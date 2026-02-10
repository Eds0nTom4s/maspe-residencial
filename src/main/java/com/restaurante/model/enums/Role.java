package com.restaurante.model.enums;

/**
 * Enum que representa os roles (perfis) de usuário no sistema
 * 
 * CLIENTE: Cliente final que faz pedidos
 * ATENDENTE: Garçom/atendente que gerencia pedidos e mesas
 * GERENTE: Gerente que tem acesso administrativo
 * COZINHA: Pessoal da cozinha que executa subpedidos
 * ADMIN: Administrador do sistema com acesso total
 */
public enum Role {
    ROLE_CLIENTE("Cliente"),
    ROLE_ATENDENTE("Atendente"),
    ROLE_GERENTE("Gerente"),
    ROLE_COZINHA("Cozinha"),
    ROLE_ADMIN("Administrador");

    private final String descricao;

    Role(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna nome sem prefixo ROLE_
     */
    public String getNomeSemPrefixo() {
        return this.name().substring(5); // Remove "ROLE_"
    }
}
