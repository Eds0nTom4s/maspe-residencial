package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;

public class DeviceSessaoParticipanteInviteRequest {

    @NotBlank
    private String telefone;

    private String nomeExibicao;

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public void setNomeExibicao(String nomeExibicao) {
        this.nomeExibicao = nomeExibicao;
    }
}

