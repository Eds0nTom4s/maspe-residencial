package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


/**
 * DTO para solicitação de OTP (One-Time Password)
 * Usado quando cliente escaneia QR Code ou faz login
 */
public class SolicitarOtpRequest {

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Formato de telefone inválido")
    private String telefone;

    public SolicitarOtpRequest() {}

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public static SolicitarOtpRequestBuilder builder() {
        return new SolicitarOtpRequestBuilder();
    }

    public static class SolicitarOtpRequestBuilder {
        private String telefone;

        public SolicitarOtpRequestBuilder telefone(String telefone) {
            this.telefone = telefone;
            return this;
        }

        public SolicitarOtpRequest build() {
            SolicitarOtpRequest request = new SolicitarOtpRequest();
            request.setTelefone(this.telefone);
            return request;
        }
    }
}
