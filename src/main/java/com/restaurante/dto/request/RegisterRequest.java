package com.restaurante.dto.request;

import com.restaurante.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username é obrigatório")
    @Size(min = 3, max = 50, message = "Username deve ter entre 3 e 50 caracteres")
    private String username;

    @NotBlank(message = "Password é obrigatório")
    @Size(min = 6, message = "Password deve ter no mínimo 6 caracteres")
    private String password;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "Nome completo é obrigatório")
    @Size(max = 150)
    private String nomeCompleto;

    @Size(max = 20)
    private String telefone;

    @NotEmpty(message = "Pelo menos um role é obrigatório")
    private Set<Role> roles;
}
