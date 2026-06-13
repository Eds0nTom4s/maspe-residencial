package com.restaurante.dto.response.kds;

public record KdsSubPedidoItemResponse(
        Long produtoId,
        String produtoNome,
        Integer quantidade,
        String observacao
) {
}
