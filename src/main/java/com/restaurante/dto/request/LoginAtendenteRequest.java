package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request para login de Atendente/Gerente/Admin.
 * Aceita autenticação por:
 *   - username + senha  (User entity)
 *   - telefone + senha  (Atendente entity, legado)
 */
public class LoginAtendenteRequest {

    /**
     * Nome de utilizador (User entity).
     * Usar este campo para login de Admin, Gerente, Atendente e Cozinha
     * criados via /api/usuarios.
     */
    private String username;

    /**
     * Telefone (Atendente entity — modo legado).
     * Mantido para compatibilidade. Prefer usar username.
     */
    private String telefone;

    @NotBlank(message = "Senha é obrigatória")
    private String senha;

    public LoginAtendenteRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
}
