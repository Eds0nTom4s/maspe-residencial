package com.restaurante.dto.response;

import lombok.Data;

import java.time.Instant;

@Data
public class PublicOtpChallengeResponse {
    private Long challengeId;
    private Instant expiresAt;
    private Instant resendAvailableAt;
    private String maskedPhone;
    private String debugOtp;
}

