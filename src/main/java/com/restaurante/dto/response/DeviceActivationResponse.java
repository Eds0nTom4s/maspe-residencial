package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceActivationResponse {
    private String deviceToken;
    private Long dispositivoId;
    private Long tenantId;
    private String tenantCode;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private DispositivoTipo tipo;
    private DispositivoStatus status;
    private List<DeviceCapability> capabilities;
}

