package com.restaurante.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantPdvCreatePedidoItemRequest {
    @NotNull
    private Long produtoId;

    @NotNull
    @Min(1)
    private Integer quantidade;

    @Size(max = 500)
    private String observacao;
}
