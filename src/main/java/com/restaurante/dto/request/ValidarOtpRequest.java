package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


/**
 * DTO para validação de OTP
 * Usado para autenticar o cliente após receber o código
 */
public class ValidarOtpRequest {

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Formato de telefone inválido")
    private String telefone;

    @NotBlank(message = "Código OTP é obrigatório")
    @Size(min = 4, max = 4, message = "Código OTP deve ter 4 dígitos")
    @Pattern(regexp = "^\\d{4}$", message = "Código OTP deve conter apenas números")
    private String codigo;

    public ValidarOtpRequest() {}

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public static ValidarOtpRequestBuilder builder() {
        return new ValidarOtpRequestBuilder();
    }

    public static class ValidarOtpRequestBuilder {
        private String telefone;
        private String codigo;

        public ValidarOtpRequestBuilder telefone(String telefone) {
            this.telefone = telefone;
            return this;
        }

        public ValidarOtpRequestBuilder codigo(String codigo) {
            this.codigo = codigo;
            return this;
        }

        public ValidarOtpRequest build() {
            ValidarOtpRequest request = new ValidarOtpRequest();
            request.setTelefone(this.telefone);
            request.setCodigo(this.codigo);
            return request;
        }
    }
}
