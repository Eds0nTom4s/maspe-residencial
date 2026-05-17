package com.restaurante.dto.request;

import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CriarTenantUsuarioRequest {

    @Size(max = 150)
    private String nome;

    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String telefone;

    @NotEmpty
    private Set<TenantUserRole> roles;

    @Size(max = 120)
    private String senhaTemporaria;

    private TenantUserEstado estadoInicial = TenantUserEstado.ATIVO;
}

