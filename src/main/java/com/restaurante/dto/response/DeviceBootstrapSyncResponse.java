package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceBootstrapSyncResponse(
        Long tenantId,
        String tenantCode,
        String tenantNome,
        Long instituicaoId,
        String instituicaoNome,
        Long unidadeAtendimentoId,
        String unidadeAtendimentoNome,
        Long unidadeProducaoId,
        String unidadeProducaoNome,
        Long dispositivoId,
        String dispositivoCodigo,
        DispositivoTipo tipo,
        DispositivoStatus status,
        List<DeviceCapability> capabilities,
        String publicBaseUrl,
        LocalDateTime serverTime,
        LocalDateTime syncGeneratedAt
) {
}

