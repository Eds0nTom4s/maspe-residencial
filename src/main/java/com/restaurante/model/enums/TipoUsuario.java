package com.restaurante.model.enums;

/**
 * Enum que representa os tipos de usuário do sistema
 */
public enum TipoUsuario {
    CLIENTE("Cliente"),
    ATENDENTE("Atendente"),
    COZINHA("Cozinha"),
    GERENTE("Gerente"),
    ADMIN("Administrador");

    private final String descricao;

    TipoUsuario(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
