package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Prompt 41.4 — Request para verificar OTP do OWNER e obter ownerActionToken.
 */
public class PublicOwnerOtpVerifyRequest {

    @NotBlank
    private Long ownerChallengeId;

    @NotBlank
    private String ownerTelefone;

    @NotBlank
    private String otp;

    public Long getOwnerChallengeId() { return ownerChallengeId; }
    public void setOwnerChallengeId(Long ownerChallengeId) { this.ownerChallengeId = ownerChallengeId; }

    public String getOwnerTelefone() { return ownerTelefone; }
    public void setOwnerTelefone(String ownerTelefone) { this.ownerTelefone = ownerTelefone; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
