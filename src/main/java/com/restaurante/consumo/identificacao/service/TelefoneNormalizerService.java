package com.restaurante.consumo.identificacao.service;

import com.restaurante.config.PhoneNormalizationProperties;
import com.restaurante.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelefoneNormalizerService {

    private final PhoneNormalizationProperties props;

    public String normalizeOrThrow(String raw) {
        if (raw == null) throw new BusinessException("PHONE_INVALID");
        String digits = raw.trim().replaceAll("[^0-9+]", "");
        digits = digits.replaceAll("\\s+", "");
        // manter apenas + e dígitos
        digits = digits.replaceAll("[^0-9+]", "");
        if (digits.isBlank()) throw new BusinessException("PHONE_INVALID");

        String defaultCc = props.getDefaultCountryCode() != null ? props.getDefaultCountryCode().trim() : "+244";
        if (!defaultCc.startsWith("+")) defaultCc = "+" + defaultCc;

        String normalized;
        if (digits.startsWith("+")) {
            normalized = digits;
        } else if (digits.startsWith("244")) {
            normalized = "+" + digits;
        } else if (digits.length() == 9 && digits.startsWith("9")) {
            normalized = defaultCc + digits;
        } else {
            throw new BusinessException("PHONE_INVALID");
        }

        // Angola mobile: +244 + 9 dígitos iniciando com 9
        if (!normalized.startsWith("+244")) throw new BusinessException("PHONE_INVALID");
        String rest = normalized.substring(4);
        if (rest.length() != 9) throw new BusinessException("PHONE_INVALID");
        if (!rest.startsWith("9")) throw new BusinessException("PHONE_INVALID");
        if (!rest.matches("\\d{9}")) throw new BusinessException("PHONE_INVALID");

        return "+244" + rest;
    }

    public String mask(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.length() < 8) return "***";
        // +244923000000 -> +244923***000
        String p = normalizedPhone;
        if (p.length() <= 8) return p;
        String prefix = p.substring(0, Math.min(7, p.length())); // +244923
        String suffix = p.substring(Math.max(p.length() - 3, 0));
        return prefix + "***" + suffix;
    }
}

