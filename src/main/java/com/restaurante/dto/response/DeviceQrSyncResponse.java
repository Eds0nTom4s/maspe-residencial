package com.restaurante.dto.response;

import com.restaurante.model.enums.QrCodeOperacionalTipo;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceQrSyncResponse(
        LocalDateTime syncGeneratedAt,
        List<DeviceQrSyncItem> qrcodes
) {
    public record DeviceQrSyncItem(
            Long id,
            QrCodeOperacionalTipo tipo,
            String nome,
            String token,
            String qrUrlPublica,
            Long instituicaoId,
            Long unidadeAtendimentoId,
            Long mesaId,
            Boolean ativo,
            Boolean revogado,
            LocalDateTime updatedAt
    ) {}
}

