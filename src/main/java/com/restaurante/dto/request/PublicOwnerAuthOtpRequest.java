package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;

public class PublicOwnerAuthOtpRequest {

    @NotBlank
    private String telefone;

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }
}

