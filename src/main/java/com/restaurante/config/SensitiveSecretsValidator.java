package com.restaurante.config;

import com.restaurante.txevidence.key.TransactionEvidenceKeyProvider;
import com.restaurante.txevidence.properties.TransactionEvidenceProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class SensitiveSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(SensitiveSecretsValidator.class);

    private final Environment environment;
    private final DeviceProperties deviceProperties;
    private final TransactionEvidenceProperties transactionEvidenceProperties;
    private final TransactionEvidenceKeyProvider transactionEvidenceKeyProvider;

    @Value("${consuma.sync.cursor.hmac-secret:dev-sync-cursor-secret-change-me}")
    private String syncCursorHmacSecret;

    @PostConstruct
    public void validate() {
        boolean prod = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("prod"));
        boolean sandbox = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("sandbox") || p.equalsIgnoreCase("sandbox-local"));

        // Device token hash secret
        String deviceTokenSecret = deviceProperties.getTokenHashSecret();
        validateSecret("DEVICE_TOKEN_HASH_SECRET", deviceTokenSecret, prod);

        // Sync cursor HMAC secret
        validateSecret("SYNC_CURSOR_HMAC_SECRET", syncCursorHmacSecret, prod);

        if (transactionEvidenceProperties.isEnabled()) {
            String keyVersion = transactionEvidenceProperties.getKeyVersion();
            String secret = transactionEvidenceKeyProvider.hmacSecretForVersion(keyVersion).orElse(null);
            validateSecret("CONSUMA_TX_EVIDENCE_HMAC_KEY_V" + keyVersion, secret, prod || sandbox);
        }
    }

    private void validateSecret(String name, String value, boolean prod) {
        String v = value != null ? value.trim() : "";
        boolean isDefault = v.equalsIgnoreCase("dev-secret-change-me")
                || v.equalsIgnoreCase("dev-sync-cursor-secret-change-me")
                || v.contains("dev-") && v.contains("change-me");

        if (prod) {
            if (v.isBlank()) {
                throw new IllegalStateException(name + " não configurado (obrigatório em produção).");
            }
            if (v.length() < 32) {
                throw new IllegalStateException(name + " muito curto (mínimo recomendado: 32 caracteres).");
            }
            if (isDefault) {
                throw new IllegalStateException(name + " está com valor default de desenvolvimento (bloqueado em produção).");
            }
            return;
        }

        // dev/test: permitir, mas alertar
        if (v.isBlank()) {
            log.warn("{} não configurado; ambiente não-prod pode falhar em fluxos device/sync.", name);
        } else if (isDefault || v.length() < 16) {
            log.warn("{} fraco/default em ambiente não-prod; usar secret forte em produção.", name);
        }
    }
}
