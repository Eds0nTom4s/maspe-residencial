package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSubPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record KdsSubPedidoResponse(
        Long subPedidoId,
        String subPedidoNumero,
        StatusSubPedido status,

        Long pedidoId,
        String pedidoNumero,

        Long unidadeProducaoId,
        String unidadeProducaoNome,

        Long mesaId,
        String mesaReferencia,
        Integer mesaNumero,

        LocalDateTime criadoEm,
        LocalDateTime iniciadoEm,
        LocalDateTime prontoEm,
        LocalDateTime entregueEm,

        Long tempoDesdeCriacaoSegundos,
        Long tempoEmPreparacaoSegundos,

        BigDecimal total,
        List<Item> itens
) {
    public record Item(
            Long itemPedidoId,
            Long produtoId,
            String produtoNome,
            Integer quantidade,
            BigDecimal precoUnitario,
            BigDecimal subtotal,
            String observacoes
    ) {}
}

