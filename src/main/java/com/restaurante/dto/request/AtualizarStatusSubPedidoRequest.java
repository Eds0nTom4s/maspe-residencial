package com.restaurante.dto.request;

import com.restaurante.model.enums.StatusSubPedido;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AtualizarStatusSubPedidoRequest {
    @NotNull
    private StatusSubPedido status;
}

