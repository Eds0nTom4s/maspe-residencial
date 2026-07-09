package com.restaurante.dto.response;

import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;

import java.time.LocalDateTime;

public record DeviceFilaEventSyncItem(
        Long eventId,
        OperationalEventType eventType,
        OperationalEntityType entityType,
        Long entityId,
        Long pedidoId,
        Long subPedidoId,
        String statusAnterior,
        String statusNovo,
        OperationalOrigem origem,
        LocalDateTime createdAt
) {
}

