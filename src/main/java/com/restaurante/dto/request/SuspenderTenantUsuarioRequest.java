package com.restaurante.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuspenderTenantUsuarioRequest {
    @Size(max = 200)
    private String motivo;
}

