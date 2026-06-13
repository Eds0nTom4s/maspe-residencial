package com.restaurante.dto.response.kds;

import com.restaurante.model.enums.KdsRealtimeEventType;
import com.restaurante.model.enums.StatusSubPedido;

import java.time.Instant;
import java.util.UUID;

public record KdsRealtimeEventResponse(
        UUID eventId,
        Long tenantId,
        KdsRealtimeEventType eventType,
        Long subPedidoId,
        Long pedidoId,
        String pedidoNumero,
        Long unidadeProducaoId,
        String unidadeProducaoNome,
        StatusSubPedido statusAnterior,
        StatusSubPedido statusAtual,
        Long version,
        Instant occurredAt
) {
}
