package com.restaurante.dto.response;

import java.time.Instant;

public class DeviceSessaoParticipanteOtpChallengeResponse {

    private Long challengeId;
    private String maskedPhone;
    private Instant expiresAt;
    private Instant resendAvailableAt;
    private String debugOtp;

    public Long getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(Long challengeId) {
        this.challengeId = challengeId;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public void setMaskedPhone(String maskedPhone) {
        this.maskedPhone = maskedPhone;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getResendAvailableAt() {
        return resendAvailableAt;
    }

    public void setResendAvailableAt(Instant resendAvailableAt) {
        this.resendAvailableAt = resendAvailableAt;
    }

    public String getDebugOtp() {
        return debugOtp;
    }

    public void setDebugOtp(String debugOtp) {
        this.debugOtp = debugOtp;
    }
}

