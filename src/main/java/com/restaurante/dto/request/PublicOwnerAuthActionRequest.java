package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PublicOwnerAuthActionRequest {

    @NotNull
    private Long ownerChallengeId;

    @NotBlank
    private String ownerTelefone;

    @NotBlank
    private String otp;

    @Size(max = 255)
    private String reason;

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

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

