package com.restaurante.dto.response.kds;

import com.restaurante.model.enums.StatusSubPedido;

import java.time.LocalDateTime;
import java.util.List;

public record KdsSubPedidoResponse(
        Long id,
        Long pedidoId,
        String pedidoNumero,
        StatusSubPedido status,
        Long unidadeProducaoId,
        String unidadeProducaoNome,
        List<KdsSubPedidoItemResponse> itens,
        String clienteNome,
        String clienteTelefone,
        Long mesaId,
        String mesaNome,
        Long sessaoId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
}
