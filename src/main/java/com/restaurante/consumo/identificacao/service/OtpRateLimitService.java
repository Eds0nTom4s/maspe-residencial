package com.restaurante.consumo.identificacao.service;

import com.restaurante.consumo.identificacao.repository.TelefoneOtpChallengeRepository;
import com.restaurante.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class OtpRateLimitService {

    private final TelefoneOtpChallengeRepository challengeRepository;

    @Value("${consuma.otp.rate-limit.max-requests-per-phone-per-hour:5}")
    private int maxRequestsPerPhonePerHour;

    @Value("${consuma.otp.rate-limit.max-requests-per-ip-per-hour:20}")
    private int maxRequestsPerIpPerHour;

    public void checkOrThrow(Long tenantId, String phoneNormalized, String clientIp) {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        long byPhone = challengeRepository.countRecentByPhone(tenantId, phoneNormalized, since);
        if (byPhone >= maxRequestsPerPhonePerHour) throw new BusinessException("OTP_RATE_LIMIT_EXCEEDED");
        if (clientIp != null && !clientIp.isBlank()) {
            long byIp = challengeRepository.countRecentByIp(tenantId, clientIp, since);
            if (byIp >= maxRequestsPerIpPerHour) throw new BusinessException("OTP_RATE_LIMIT_EXCEEDED");
        }
    }
}

