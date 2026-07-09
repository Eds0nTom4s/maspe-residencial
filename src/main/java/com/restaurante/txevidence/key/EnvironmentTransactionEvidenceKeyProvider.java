package com.restaurante.txevidence.key;

import com.restaurante.txevidence.properties.TransactionEvidenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EnvironmentTransactionEvidenceKeyProvider implements TransactionEvidenceKeyProvider {

    private final TransactionEvidenceProperties props;

    @Override
    public String activeKeyVersion() {
        return props.getKeyVersion();
    }

    @Override
    public Optional<String> hmacSecretForVersion(String keyVersion) {
        String v = keyVersion != null ? keyVersion.trim() : null;
        if (v == null || v.isBlank()) return Optional.empty();
        String envVar = "CONSUMA_TX_EVIDENCE_HMAC_KEY_V" + v;
        String secret = System.getenv(envVar);
        if (secret != null && !secret.isBlank()) return Optional.of(secret);

        // Dev/test fallback (must remain empty in production).
        String fallback = props.getDevHmacSecret();
        if (fallback == null || fallback.isBlank()) return Optional.empty();
        return Optional.of(fallback);
    }
}
