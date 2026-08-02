package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "A palavra-passe atual é obrigatória.") String currentPassword,
        @NotBlank(message = "A nova palavra-passe é obrigatória.") String newPassword,
        @NotBlank(message = "A confirmação da palavra-passe é obrigatória.") String confirmPassword
) {}
