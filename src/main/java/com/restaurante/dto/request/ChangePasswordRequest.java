package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "Senha atual e obrigatoria.")
    private String currentPassword;

    @NotBlank(message = "Nova senha e obrigatoria.")
    private String newPassword;

    @NotBlank(message = "Confirmacao da senha e obrigatoria.")
    private String confirmPassword;
}
