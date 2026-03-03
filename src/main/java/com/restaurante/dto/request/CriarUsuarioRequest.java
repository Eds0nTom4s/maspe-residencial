package com.restaurante.dto.request;

import com.restaurante.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request para criação de utilizador do sistema (uso exclusivo ADMIN).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriarUsuarioRequest {

    @NotBlank(message = "Username é obrigatório")
    @Size(min = 3, max = 50, message = "Username deve ter entre 3 e 50 caracteres")
    private String username;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    private String senha;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    private String nomeCompleto;

    private String telefone;

    @NotEmpty(message = "Ao menos uma role é obrigatória")
    private Set<Role> roles;
}
