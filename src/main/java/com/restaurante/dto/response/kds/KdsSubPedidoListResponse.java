package com.restaurante.dto.response.kds;

import java.util.List;

public record KdsSubPedidoListResponse(
        List<KdsSubPedidoResponse> items,
        KdsSummaryResponse summary
) {
}
