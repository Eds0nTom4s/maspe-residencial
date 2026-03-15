package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompletarPerfilRequest {
    @NotBlank(message = "O nome é obrigatório")
    private String nome;
}
