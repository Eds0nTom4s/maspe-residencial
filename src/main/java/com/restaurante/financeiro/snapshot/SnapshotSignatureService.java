package com.restaurante.financeiro.snapshot;

import com.restaurante.financeiro.snapshot.dto.SnapshotSignatureResult;
import com.restaurante.financeiro.snapshot.dto.SnapshotSignatureVerificationResult;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class SnapshotSignatureService {

    private final SnapshotIntegridadeProperties props;

    public SnapshotSignatureService(SnapshotIntegridadeProperties props) {
        this.props = props;
    }

    public SnapshotSignatureResult sign(String snapshotHash) {
        if (!props.isSignatureEnabled()) {
            throw new IllegalStateException("Assinatura de snapshot desativada.");
        }

        Keyring keyring = Keyring.resolve(props);
        if (keyring.activeKeyId == null || keyring.activeKey == null || keyring.activeKey.getStatus() != SnapshotSignatureKeyStatus.ACTIVE) {
            throw new IllegalStateException("Keyring inválido: activeKeyId ausente/inválido.");
        }

        SnapshotSignatureResult r = new SnapshotSignatureResult();
        r.setAlgorithm(props.getSignatureAlgorithm());
        r.setKeyId(keyring.activeKeyId);
        r.setGeneratedAt(LocalDateTime.now());
        r.setSignature(hmacSha256Hex(snapshotHash, keyring.activeKey.getSecret()));
        return r;
    }

    public SnapshotSignatureVerificationResult verify(String snapshotHash, String signatureHex, String keyId) {
        SnapshotSignatureVerificationResult out = new SnapshotSignatureVerificationResult();
        out.setAlgorithm(props.getSignatureAlgorithm());
        out.setKeyId(keyId);

        if (!props.isSignatureEnabled()) {
            out.setSignatureValid(true);
            out.setKeyFound(true);
            out.setKeyStatus(null);
            out.setFailureReason(null);
            return out;
        }

        if (keyId == null || keyId.isBlank()) {
            out.setKeyFound(false);
            out.setSignatureValid(false);
            out.setFailureReason(SnapshotSignatureFailureReason.KEY_ID_MISSING);
            return out;
        }
        Keyring keyring = Keyring.resolve(props);
        SnapshotSignatureKeyProperties key = keyring.keys.get(keyId);
        if (key == null) {
            out.setKeyFound(false);
            out.setSignatureValid(false);
            out.setFailureReason(SnapshotSignatureFailureReason.KEY_NOT_FOUND);
            return out;
        }

        out.setKeyFound(true);
        out.setKeyStatus(key.getStatus());

        if (signatureHex == null || signatureHex.isBlank()) {
            out.setSignatureValid(false);
            out.setFailureReason(SnapshotSignatureFailureReason.SIGNATURE_MISSING);
            return out;
        }

        if (key.getStatus() == SnapshotSignatureKeyStatus.DISABLED) {
            out.setSignatureValid(false);
            out.setFailureReason(SnapshotSignatureFailureReason.KEY_DISABLED);
            return out;
        }
        if (key.getSecret() == null || key.getSecret().isBlank()) {
            out.setSignatureValid(false);
            out.setFailureReason(SnapshotSignatureFailureReason.SECRET_UNAVAILABLE);
            return out;
        }

        String expected = hmacSha256Hex(snapshotHash, key.getSecret());
        boolean ok = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHex.getBytes(StandardCharsets.UTF_8)
        );
        out.setSignatureValid(ok);
        if (!ok) {
            out.setFailureReason(SnapshotSignatureFailureReason.SIGNATURE_MISMATCH);
        }
        return out;
    }

    private static String hmacSha256Hex(String snapshotHash, String secret) {
        if (snapshotHash == null || snapshotHash.isBlank()) {
            throw new IllegalArgumentException("snapshotHash é obrigatório para assinatura.");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Secret HMAC ausente para assinatura do snapshot.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(snapshotHash.getBytes(StandardCharsets.UTF_8));
            return toHexLower(out);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar snapshotHash via HMAC-SHA256.", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static final class Keyring {
        final String activeKeyId;
        final SnapshotSignatureKeyProperties activeKey;
        final Map<String, SnapshotSignatureKeyProperties> keys;

        Keyring(String activeKeyId, SnapshotSignatureKeyProperties activeKey, Map<String, SnapshotSignatureKeyProperties> keys) {
            this.activeKeyId = activeKeyId;
            this.activeKey = activeKey;
            this.keys = keys;
        }

        static Keyring resolve(SnapshotIntegridadeProperties props) {
            Map<String, SnapshotSignatureKeyProperties> keys = props.getKeys();
            if (keys == null || keys.isEmpty()) {
                SnapshotSignatureKeyProperties legacy = new SnapshotSignatureKeyProperties();
                legacy.setStatus(SnapshotSignatureKeyStatus.ACTIVE);
                legacy.setSecret(props.getSignatureSecret());
                String keyId = props.getSignatureKeyId() != null ? props.getSignatureKeyId() : "platform-snapshot-key-v1";
                return new Keyring(keyId, legacy, Map.of(keyId, legacy));
            }
            String activeKeyId = props.getActiveKeyId();
            SnapshotSignatureKeyProperties active = activeKeyId != null ? keys.get(activeKeyId) : null;
            return new Keyring(activeKeyId, active, keys);
        }
    }
}
