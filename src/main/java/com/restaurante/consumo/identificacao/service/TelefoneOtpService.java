package com.restaurante.consumo.identificacao.service;

import com.restaurante.config.OtpProperties;
import com.restaurante.consumo.identificacao.entity.TelefoneOtpChallenge;
import com.restaurante.consumo.identificacao.repository.TelefoneOtpChallengeRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OtpPurpose;
import com.restaurante.model.enums.OtpStatus;
import com.restaurante.notificacao.service.NotificacaoService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelefoneOtpService {

    private final OtpProperties props;
    private final TelefoneNormalizerService phoneNormalizerService;
    private final OtpRateLimitService rateLimitService;
    private final TelefoneOtpChallengeRepository repository;
    private final NotificacaoService notificacaoService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public OtpRequestResult requestOtp(Tenant tenant,
                                      String rawPhone,
                                      OtpPurpose purpose,
                                      SessaoConsumo sessaoConsumo,
                                      String clientIp,
                                      String userAgent) {
        if (!props.isEnabled()) throw new BusinessException("OTP_DISABLED");
        if (purpose == null) purpose = OtpPurpose.IDENTIFICAR_SESSAO;

        String phone = phoneNormalizerService.normalizeOrThrow(rawPhone);
        rateLimitService.checkOrThrow(tenant.getId(), phone, clientIp);

        Instant now = Instant.now();
        List<TelefoneOtpChallenge> actives = repository.findActivePendingByPhone(tenant.getId(), phone, now);
        TelefoneOtpChallenge challenge;

        if (!actives.isEmpty()) {
            challenge = actives.getFirst();
            // max-active-challenges-per-phone=1: só permite reenvio do mesmo purpose/sessao
            Long existingSessaoId = challenge.getSessaoConsumo() != null ? challenge.getSessaoConsumo().getId() : null;
            Long requestedSessaoId = sessaoConsumo != null ? sessaoConsumo.getId() : null;
            if (challenge.getPurpose() != purpose || (existingSessaoId != null && !existingSessaoId.equals(requestedSessaoId)) || (existingSessaoId == null && requestedSessaoId != null)) {
                throw new BusinessException("OTP_MAX_ACTIVE_CHALLENGES");
            }
            if (challenge.getResendCount() >= props.getMaxResends()) throw new BusinessException("OTP_MAX_RESENDS_EXCEEDED");
            Instant availableAt = challenge.getLastSentAt().plusSeconds(props.getResendCooldownSeconds());
            if (availableAt.isAfter(now)) throw new BusinessException("OTP_RESEND_TOO_SOON");

            String otp = generateOtp();
            challenge.setOtpHash(hashOtp(challenge.getId(), otp));
            challenge.setExpiresAt(now.plus(props.getTtlMinutes(), ChronoUnit.MINUTES));
            challenge.setResendCount(challenge.getResendCount() + 1);
            challenge.setLastSentAt(now);
            challenge.setClientIp(clientIp);
            challenge.setUserAgent(userAgent);
            repository.save(challenge);

            boolean smsSent = trySendSms(phone, otp);
            return new OtpRequestResult(challenge, phoneNormalizerService.mask(phone), otpIfDebug(otp), availableAt, smsSent);
        }

        // novo challenge
        challenge = new TelefoneOtpChallenge();
        challenge.setTenant(tenant);
        challenge.setTelefoneNormalizado(phone);
        challenge.setPurpose(purpose);
        challenge.setStatus(OtpStatus.PENDING);
        challenge.setMaxAttempts(props.getMaxAttempts());
        challenge.setAttempts(0);
        challenge.setResendCount(0);
        challenge.setLastSentAt(now);
        challenge.setExpiresAt(now.plus(props.getTtlMinutes(), ChronoUnit.MINUTES));
        challenge.setClientIp(clientIp);
        challenge.setUserAgent(userAgent);
        challenge.setSessaoConsumo(sessaoConsumo);
        repository.save(challenge);

        String otp = generateOtp();
        challenge.setOtpHash(hashOtp(challenge.getId(), otp));
        repository.save(challenge);

        boolean smsSent = trySendSms(phone, otp);

        Instant resendAvailableAt = now.plusSeconds(props.getResendCooldownSeconds());
        return new OtpRequestResult(challenge, phoneNormalizerService.mask(phone), otpIfDebug(otp), resendAvailableAt, smsSent);
    }

    @Transactional
    public TelefoneOtpChallenge verifyOtp(Long tenantId, Long challengeId, String rawPhone, String otp) {
        String phone = phoneNormalizerService.normalizeOrThrow(rawPhone);
        TelefoneOtpChallenge c = repository.findByIdAndTenant_Id(challengeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("OTP_CHALLENGE_NOT_FOUND"));

        if (!phone.equals(c.getTelefoneNormalizado())) throw new BusinessException("OTP_INVALID");
        Instant now = Instant.now();
        if (c.getStatus() == OtpStatus.CONSUMED) throw new BusinessException("OTP_ALREADY_USED");
        if (c.getStatus() == OtpStatus.BLOCKED) throw new BusinessException("OTP_MAX_ATTEMPTS_EXCEEDED");
        if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(now)) {
            c.setStatus(OtpStatus.EXPIRED);
            repository.save(c);
            throw new BusinessException("OTP_CHALLENGE_EXPIRED");
        }

        if (otp == null || otp.isBlank()) throw new BusinessException("OTP_INVALID");
        boolean ok = hashOtp(c.getId(), otp.trim()).equals(c.getOtpHash());
        if (!ok) {
            c.setAttempts(c.getAttempts() + 1);
            if (c.getAttempts() >= c.getMaxAttempts()) {
                c.setStatus(OtpStatus.BLOCKED);
            }
            repository.save(c);
            throw new BusinessException("OTP_INVALID");
        }

        c.setStatus(OtpStatus.CONSUMED);
        c.setConsumedAt(now);
        repository.save(c);
        return c;
    }

    private boolean trySendSms(String phoneNormalized, String otp) {
        // Nunca logar OTP aqui.
        try {
            return notificacaoService.enviarOtp(phoneNormalized, otp);
        } catch (Exception e) {
            return false;
        }
    }

    private String generateOtp() {
        int len = Math.max(4, props.getLength());
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(secureRandom.nextInt(10));
        return sb.toString();
    }

    private String otpIfDebug(String otp) {
        return props.isMockEnabled() ? otp : null;
    }

    private String hashOtp(Long challengeId, String otp) {
        try {
            String pepper = props.getHashPepper() != null ? props.getHashPepper() : "";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal((challengeId + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new BusinessException("OTP_HASH_ERROR");
        }
    }

    @Getter
    public static class OtpRequestResult {
        private final TelefoneOtpChallenge challenge;
        private final String maskedPhone;
        private final String debugOtp;
        private final Instant resendAvailableAt;
        private final boolean smsSent;

        public OtpRequestResult(TelefoneOtpChallenge challenge, String maskedPhone, String debugOtp, Instant resendAvailableAt, boolean smsSent) {
            this.challenge = challenge;
            this.maskedPhone = maskedPhone;
            this.debugOtp = debugOtp;
            this.resendAvailableAt = resendAvailableAt;
            this.smsSent = smsSent;
        }
    }
}
