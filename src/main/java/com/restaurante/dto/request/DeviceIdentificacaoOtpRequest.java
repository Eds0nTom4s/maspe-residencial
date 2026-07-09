package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceIdentificacaoOtpRequest {
    @NotBlank
    private String telefone;
}

