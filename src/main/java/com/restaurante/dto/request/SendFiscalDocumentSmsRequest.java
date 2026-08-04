package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendFiscalDocumentSmsRequest(
        @NotBlank @Size(max = 30) String phone
) {}
