package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request para login de Atendente/Gerente
 */
@Data
public class LoginAtendenteRequest {
    
    @NotBlank(message = "Telefone é obrigatório")
    private String telefone;
    
    @NotBlank(message = "Senha é obrigatória")
    private String senha;
}
