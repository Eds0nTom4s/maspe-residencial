package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;

public class EnviarFaturaSmsRequest {

    @NotBlank(message = "Telefone é obrigatório para envio por SMS")
    private String telefone;

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }
}
