package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceActivationRequest {

    @NotBlank
    @Size(min = 6, max = 20)
    private String activationCode;

    @Size(max = 60)
    private String codigo;

    @Size(max = 40)
    private String appVersion;

    @Size(max = 30)
    private String platform;

    @Size(max = 80)
    private String modeloDispositivo;

    @Size(max = 80)
    private String fabricante;
}

