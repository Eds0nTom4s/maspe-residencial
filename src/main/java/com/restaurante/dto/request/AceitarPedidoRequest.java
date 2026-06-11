package com.restaurante.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AceitarPedidoRequest {

    @Size(max = 500)
    private String observacao;
}
