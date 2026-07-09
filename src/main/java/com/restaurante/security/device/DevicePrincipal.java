package com.restaurante.security.device;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;

import java.util.List;

public record DevicePrincipal(
        Long dispositivoId,
        String dispositivoCodigo,
        Long tenantId,
        String tenantCode,
        Long instituicaoId,
        Long unidadeAtendimentoId,
        Long unidadeProducaoId,
        DispositivoTipo tipo,
        DispositivoStatus status,
        List<DeviceCapability> capabilities,
        Integer tokenVersion
) {
}
