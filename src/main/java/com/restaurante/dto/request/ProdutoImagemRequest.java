package com.restaurante.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProdutoImagemRequest {

    @Size(max = 200, message = "Legenda deve ter no máximo 200 caracteres")
    private String legenda;
}
