package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para validação de OTP
 * Usado para autenticar o cliente após receber o código
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidarOtpRequest {

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Formato de telefone inválido")
    private String telefone;

    @NotBlank(message = "Código OTP é obrigatório")
    @Size(min = 4, max = 10, message = "Código OTP inválido")
    private String codigo;
}
