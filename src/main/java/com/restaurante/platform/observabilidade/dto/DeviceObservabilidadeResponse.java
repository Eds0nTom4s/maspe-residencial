package com.restaurante.platform.observabilidade.dto;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeviceObservabilidadeResponse {
    private Long deviceId;
    private String nome;
    private DispositivoTipo tipo;
    private DispositivoStatus status;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long unidadeProducaoId;
    private LocalDateTime ultimoHeartbeatEm;
    private LocalDateTime lastAuthAt;
    private LocalDateTime lastAuthFailureAt;
    private Integer tokenVersion;
    private boolean offline;
    private long offlineMinutes;
    private List<DeviceCapability> capabilities;
    private PlatformAlertLevel alertLevel;
    private PlatformActionRecommended actionRecommended;
}

