package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejeitarPedidoRequest {

    @NotBlank(message = "Motivo é obrigatório")
    @Size(max = 500)
    private String motivo;
}
