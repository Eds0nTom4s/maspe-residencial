package com.restaurante.dto.response;

import lombok.Data;

import java.time.Instant;

@Data
public class DeviceIdentificacaoOtpResponse {
    private Long challengeId;
    private Long sessaoConsumoId;
    private String telefoneMascarado;
    private Instant expiresAt;
    private Instant resendAvailableAt;
    private String debugOtp;
}

