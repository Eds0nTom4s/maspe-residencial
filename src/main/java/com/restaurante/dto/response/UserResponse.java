package com.restaurante.dto.response;

import com.restaurante.model.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String nomeCompleto;
    private String telefone;
    private Set<Role> roles;
    private Boolean ativo;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime ultimoAcesso;
}
