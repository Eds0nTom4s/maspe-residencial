package com.restaurante.dto.response;

import java.time.LocalDateTime;

public record DeviceTokenRotateResponse(
        String deviceToken,
        Integer tokenVersion,
        LocalDateTime rotatedAt
) {
}

