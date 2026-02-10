package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para solicitação de OTP (One-Time Password)
 * Usado quando cliente escaneia QR Code ou faz login
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitarOtpRequest {

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Formato de telefone inválido")
    private String telefone;
}
