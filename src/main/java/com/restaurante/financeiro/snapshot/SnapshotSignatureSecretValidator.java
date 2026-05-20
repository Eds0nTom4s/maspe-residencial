package com.restaurante.financeiro.snapshot;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Garante que em produção não rodamos com assinatura habilitada sem segredo HMAC forte.
 */
@Component
@Profile("prod")
public class SnapshotSignatureSecretValidator {

    public SnapshotSignatureSecretValidator(SnapshotIntegridadeProperties props) {
        if (props == null) return;
        if (!props.isSignatureEnabled()) return;

        String secret = props.getSignatureSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("CONSUMA_SNAPSHOT_HMAC_SECRET obrigatório em produção quando snapshot signature está habilitada.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("CONSUMA_SNAPSHOT_HMAC_SECRET muito curto; usar >= 32 caracteres em produção.");
        }
    }
}

