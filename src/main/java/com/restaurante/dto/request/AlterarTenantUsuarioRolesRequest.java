package com.restaurante.dto.request;

import com.restaurante.model.enums.TenantUserRole;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class AlterarTenantUsuarioRolesRequest {
    @NotEmpty
    private Set<TenantUserRole> roles;
}

