package com.restaurante.dto.request;

import com.restaurante.model.enums.StatusSubPedido;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AtualizarStatusSubPedidoRequest {
    @NotNull
    private StatusSubPedido status;

    @Size(max = 500)
    private String motivo;
}
