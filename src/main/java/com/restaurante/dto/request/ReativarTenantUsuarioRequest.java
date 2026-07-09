package com.restaurante.dto.request;

import com.restaurante.model.enums.TenantUserRole;
import lombok.Data;

import java.util.Set;

@Data
public class ReativarTenantUsuarioRequest {
    private Set<TenantUserRole> roles;
}

