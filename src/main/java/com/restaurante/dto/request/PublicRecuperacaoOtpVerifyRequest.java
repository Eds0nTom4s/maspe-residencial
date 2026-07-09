package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PublicRecuperacaoOtpVerifyRequest {
    @NotNull
    private Long challengeId;
    @NotBlank
    private String telefone;
    @NotBlank
    private String otp;
}

