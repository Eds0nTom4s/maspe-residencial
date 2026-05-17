package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceConfigResponse {
    private TenantInfo tenant;
    private InstituicaoInfo instituicao;
    private UnidadeAtendimentoInfo unidadeAtendimento;
    private DispositivoInfo dispositivo;
    private List<DeviceCapability> capabilities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInfo {
        private Long id;
        private String nome;
        private String tenantCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstituicaoInfo {
        private Long id;
        private String nome;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnidadeAtendimentoInfo {
        private Long id;
        private String nome;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispositivoInfo {
        private Long id;
        private String nome;
        private String codigo;
        private DispositivoTipo tipo;
        private DispositivoStatus status;
        private String appVersion;
        private String platform;
        private LocalDateTime ultimoHeartbeatEm;
        private LocalDateTime ativadoEm;
    }
}

