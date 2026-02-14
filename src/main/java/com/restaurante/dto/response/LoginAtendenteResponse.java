package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoUsuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response do login de Atendente/Gerente
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAtendenteResponse {
    
    private Long id;
    private String nome;
    private String telefone;
    private String email;
    private TipoUsuario tipoUsuario;
    private String token;
    private Long expiresIn; // Tempo em segundos até expirar
}
