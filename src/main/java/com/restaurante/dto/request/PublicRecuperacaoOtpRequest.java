package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicRecuperacaoOtpRequest {
    @NotBlank
    private String telefone;
}

