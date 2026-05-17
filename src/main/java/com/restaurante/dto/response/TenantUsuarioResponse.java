package com.restaurante.dto.response;

import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantUsuarioResponse {
    private Long userId;
    private String nome;
    private String email;
    private String telefone;
    private Set<TenantUserRole> roles;
    private TenantUserEstado estado;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime ultimoLoginEm;
}

