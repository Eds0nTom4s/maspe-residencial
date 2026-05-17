package com.restaurante.security.device;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;

import java.util.List;

public record DevicePrincipal(
        Long dispositivoId,
        Long tenantId,
        String tenantCode,
        Long instituicaoId,
        Long unidadeAtendimentoId,
        DispositivoTipo tipo,
        DispositivoStatus status,
        List<DeviceCapability> capabilities
) {
}

