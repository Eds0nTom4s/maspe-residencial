package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request para alteração de senha de utilizador por ADMIN.
 */
public class AlterarSenhaAdminRequest {

    @NotBlank(message = "Nova senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    private String novaSenha;

    public AlterarSenhaAdminRequest() {}

    public AlterarSenhaAdminRequest(String novaSenha) {
        this.novaSenha = novaSenha;
    }

    public String getNovaSenha() { return novaSenha; }
    public void setNovaSenha(String novaSenha) { this.novaSenha = novaSenha; }
}
