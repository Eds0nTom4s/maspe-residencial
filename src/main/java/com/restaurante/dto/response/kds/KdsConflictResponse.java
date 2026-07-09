package com.restaurante.dto.response.kds;

public record KdsConflictResponse(
        String code,
        String message,
        String currentStatus,
        Long currentVersion
) {
}
