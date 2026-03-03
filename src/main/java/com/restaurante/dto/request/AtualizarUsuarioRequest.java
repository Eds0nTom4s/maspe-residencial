package com.restaurante.dto.request;

import com.restaurante.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request para actualização de dados de utilizador (uso exclusivo ADMIN).
 * Todos os campos são opcionais — apenas os não-nulos são actualizados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtualizarUsuarioRequest {

    @Email(message = "Email inválido")
    private String email;

    private String nomeCompleto;

    private String telefone;

    /**
     * Substitui o conjunto de roles do utilizador. Nulo = não altera.
     */
    private Set<Role> roles;
}
