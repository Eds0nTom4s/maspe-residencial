package com.restaurante.dto.response.kds;

public record KdsSummaryResponse(
        long pendentes,
        long emPreparacao,
        long prontos,
        long entregues,
        long total
) {
}
