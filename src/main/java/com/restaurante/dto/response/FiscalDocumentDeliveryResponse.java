package com.restaurante.dto.response;

import java.time.LocalDateTime;

public record FiscalDocumentDeliveryResponse(
        Long documentId,
        String maskedPhone,
        LocalDateTime linkExpiresAt
) {}
