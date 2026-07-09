package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceMesasSyncResponse(
        LocalDateTime syncGeneratedAt,
        List<DeviceMesaSyncItem> mesas
) {
    public record DeviceMesaSyncItem(
            Long id,
            Long unidadeAtendimentoId,
            Integer numero,
            String referencia,
            String status,
            Boolean ativa,
            Long qrCodeId,
            String qrUrlPublica,
            LocalDateTime updatedAt
    ) {}
}

