package com.restaurante.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para item de pedido (usado dentro de CriarPedidoRequest)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedidoRequest {

    @NotNull(message = "ID do produto é obrigatório")
    private Long produtoId;

    @NotNull(message = "Quantidade é obrigatória")
    @Min(value = 1, message = "Quantidade deve ser maior que zero")
    private Integer quantidade;

    private String observacoes;
}
