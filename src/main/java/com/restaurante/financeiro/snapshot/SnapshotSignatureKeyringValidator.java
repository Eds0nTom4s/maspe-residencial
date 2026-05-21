package com.restaurante.financeiro.snapshot;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Valida keyring de assinatura HMAC para snapshots.
 *
 * Em produção:
 * - exige activeKeyId e exatamente 1 chave ACTIVE
 * - ACTIVE/DEPRECATED precisam de secret forte
 */
@Component
@Profile("prod")
public class SnapshotSignatureKeyringValidator {

    public SnapshotSignatureKeyringValidator(SnapshotIntegridadeProperties props) {
        if (props == null) return;
        if (!props.isSignatureEnabled()) return;

        KeyringResolved resolved = KeyringResolved.resolve(props);

        if (resolved.activeKeyId == null || resolved.activeKeyId.isBlank()) {
            throw new IllegalStateException("active-key-id obrigatório em produção quando assinatura de snapshot está habilitada.");
        }
        if (resolved.activeKey == null || resolved.activeKey.getStatus() != SnapshotSignatureKeyStatus.ACTIVE) {
            throw new IllegalStateException("activeKeyId deve existir e ter status ACTIVE.");
        }
        if (resolved.activeCount != 1) {
            throw new IllegalStateException("Keyring inválido: deve existir exatamente 1 chave ACTIVE.");
        }

        for (Map.Entry<String, SnapshotSignatureKeyProperties> e : resolved.keys.entrySet()) {
            String keyId = e.getKey();
            SnapshotSignatureKeyProperties kp = e.getValue();
            if (kp == null) continue;
            if (kp.getStatus() == SnapshotSignatureKeyStatus.ACTIVE || kp.getStatus() == SnapshotSignatureKeyStatus.DEPRECATED) {
                String secret = kp.getSecret();
                if (secret == null || secret.isBlank()) {
                    throw new IllegalStateException("Secret obrigatório para chave " + keyId + " (ACTIVE/DEPRECATED).");
                }
                if (secret.length() < 32) {
                    throw new IllegalStateException("Secret muito curto para chave " + keyId + "; usar >= 32 caracteres em produção.");
                }
            }
        }
    }

    static final class KeyringResolved {
        final String activeKeyId;
        final Map<String, SnapshotSignatureKeyProperties> keys;
        final SnapshotSignatureKeyProperties activeKey;
        final int activeCount;

        KeyringResolved(String activeKeyId,
                        Map<String, SnapshotSignatureKeyProperties> keys,
                        SnapshotSignatureKeyProperties activeKey,
                        int activeCount) {
            this.activeKeyId = activeKeyId;
            this.keys = keys;
            this.activeKey = activeKey;
            this.activeCount = activeCount;
        }

        static KeyringResolved resolve(SnapshotIntegridadeProperties props) {
            // Se keyring não foi configurado, cai no legacy (signatureSecret/signatureKeyId).
            Map<String, SnapshotSignatureKeyProperties> keys = props.getKeys();
            if (keys == null || keys.isEmpty()) {
                SnapshotSignatureKeyProperties legacy = new SnapshotSignatureKeyProperties();
                legacy.setStatus(SnapshotSignatureKeyStatus.ACTIVE);
                legacy.setSecret(props.getSignatureSecret());
                String keyId = props.getSignatureKeyId() != null ? props.getSignatureKeyId() : "platform-snapshot-key-v1";
                keys = Map.of(keyId, legacy);
                return new KeyringResolved(keyId, keys, legacy, 1);
            }

            String activeKeyId = props.getActiveKeyId();
            int activeCount = 0;
            SnapshotSignatureKeyProperties active = null;
            for (SnapshotSignatureKeyProperties kp : keys.values()) {
                if (kp != null && kp.getStatus() == SnapshotSignatureKeyStatus.ACTIVE) activeCount++;
            }
            if (activeKeyId != null) {
                active = keys.get(activeKeyId);
            }
            return new KeyringResolved(activeKeyId, keys, active, activeCount);
        }
    }
}

