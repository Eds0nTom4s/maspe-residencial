package com.restaurante.consumo.identificacao.service;

import com.restaurante.consumo.identificacao.repository.TelefoneOtpChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OtpRateLimitService {

    private final TelefoneOtpChallengeRepository challengeRepository;

    @Value("${consuma.otp.rate-limit-per-phone:${consuma.otp.rate-limit.max-requests-per-phone-per-hour:5}}")
    private int maxRequestsPerPhone;

    @Value("${consuma.otp.rate-limit-per-ip:${consuma.otp.rate-limit.max-requests-per-ip-per-hour:20}}")
    private int maxRequestsPerIp;

    @Value("${consuma.otp.rate-limit-window-seconds:3600}")
    private int rateLimitWindowSeconds;

    public void checkOrThrow(Long tenantId, String phoneNormalized, String clientIp) {
        Instant since = Instant.now().minusSeconds(rateLimitWindowSeconds);
        long byPhone = challengeRepository.countRecentByPhone(tenantId, phoneNormalized, since);
        if (byPhone >= maxRequestsPerPhone) throw tooManyRequests();
        if (clientIp != null && !clientIp.isBlank()) {
            long byIp = challengeRepository.countRecentByIp(tenantId, clientIp, since);
            if (byIp >= maxRequestsPerIp) throw tooManyRequests();
        }
    }

    private ResponseStatusException tooManyRequests() {
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas. Aguarde alguns minutos antes de solicitar novo código."
        );
    }
}
