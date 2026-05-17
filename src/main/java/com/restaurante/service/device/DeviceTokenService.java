package com.restaurante.service.device;

import com.restaurante.config.DeviceProperties;
import com.restaurante.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String ACTIVATION_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int DEFAULT_ACTIVATION_CODE_LEN = 8;
    private static final int DEFAULT_DEVICE_TOKEN_BYTES = 32;

    private final DeviceProperties deviceProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateActivationCode() {
        StringBuilder sb = new StringBuilder(DEFAULT_ACTIVATION_CODE_LEN);
        for (int i = 0; i < DEFAULT_ACTIVATION_CODE_LEN; i++) {
            int idx = secureRandom.nextInt(ACTIVATION_ALPHABET.length());
            sb.append(ACTIVATION_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    public String generateDeviceToken() {
        byte[] bytes = new byte[DEFAULT_DEVICE_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToHex(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Valor inválido para hash.");
        }
        String secret = deviceProperties.getTokenHashSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException("DEVICE_TOKEN_HASH_SECRET não configurado.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] out = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return toHex(out);
        } catch (Exception e) {
            throw new BusinessException("Falha ao gerar hash.");
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

