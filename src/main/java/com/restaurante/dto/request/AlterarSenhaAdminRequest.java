package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para alteração de senha de utilizador por ADMIN.
 * O admin define directamente a nova senha (sem necessidade de senha antiga).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlterarSenhaAdminRequest {

    @NotBlank(message = "Nova senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    private String novaSenha;
}
