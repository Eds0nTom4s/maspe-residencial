package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PublicOwnerInviteRequest {

    @NotNull
    private Long ownerChallengeId;

    @NotBlank
    private String ownerTelefone;

    @NotBlank
    private String ownerOtp;

    @NotBlank
    private String telefoneConvidado;

    @Size(max = 120)
    private String nomeExibicao;

    public Long getOwnerChallengeId() {
        return ownerChallengeId;
    }

    public void setOwnerChallengeId(Long ownerChallengeId) {
        this.ownerChallengeId = ownerChallengeId;
    }

    public String getOwnerTelefone() {
        return ownerTelefone;
    }

    public void setOwnerTelefone(String ownerTelefone) {
        this.ownerTelefone = ownerTelefone;
    }

    public String getOwnerOtp() {
        return ownerOtp;
    }

    public void setOwnerOtp(String ownerOtp) {
        this.ownerOtp = ownerOtp;
    }

    public String getTelefoneConvidado() {
        return telefoneConvidado;
    }

    public void setTelefoneConvidado(String telefoneConvidado) {
        this.telefoneConvidado = telefoneConvidado;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public void setNomeExibicao(String nomeExibicao) {
        this.nomeExibicao = nomeExibicao;
    }
}

