package com.restaurante.dto.request;

import com.restaurante.model.enums.StatusPedido;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AtualizarStatusPedidoRequest {
    @NotNull
    private StatusPedido status;

    @Size(max = 500)
    private String motivo;
}

