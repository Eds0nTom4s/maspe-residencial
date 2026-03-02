package com.restaurante.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request para actualizar dados de um cliente (pelo admin/gerente)
 */
@Data
public class AtualizarClienteRequest {

    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;
}
