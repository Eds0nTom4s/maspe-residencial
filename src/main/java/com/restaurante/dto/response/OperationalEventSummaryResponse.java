package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record OperationalEventSummaryResponse(
        LocalDateTime de,
        LocalDateTime ate,
        long totalEventos,
        Map<String, Long> porEventType,
        Map<String, Long> porOrigem
) {
}

