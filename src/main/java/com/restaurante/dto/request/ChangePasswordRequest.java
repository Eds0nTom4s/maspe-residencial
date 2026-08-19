package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "A palavra-passe atual é obrigatória.") String currentPassword,
        @NotBlank(message = "A nova palavra-passe é obrigatória.")
        @Size(min = 12, max = 200, message = "A nova palavra-passe deve ter entre 12 e 200 caracteres.") String newPassword,
        @NotBlank(message = "A confirmação da palavra-passe é obrigatória.") String confirmPassword
) {}
