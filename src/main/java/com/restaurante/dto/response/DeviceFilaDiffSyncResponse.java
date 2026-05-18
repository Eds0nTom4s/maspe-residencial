package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceFilaDiffSyncResponse(
        LocalDateTime syncGeneratedAt,
        Long lastEventId,
        boolean hasMore,
        List<DeviceFilaEventSyncItem> eventos,
        List<Long> affectedSubPedidoIds,
        List<KdsSubPedidoResponse> subPedidosAtualizados
) {
}

