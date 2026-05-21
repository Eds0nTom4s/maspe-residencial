package com.restaurante.dto.request;

import com.restaurante.model.enums.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicIdentificacaoOtpRequest {
    @NotBlank
    private String telefone;
    private OtpPurpose purpose;
}

