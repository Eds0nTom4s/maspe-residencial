package com.restaurante.txevidence.hash;

import com.restaurante.model.enums.TransactionEvidenceAlgorithm;
import com.restaurante.txevidence.key.TransactionEvidenceKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TransactionEvidenceHashService {

    private final TransactionEvidenceKeyProvider keyProvider;

    public String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(String.valueOf(input).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    public String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(String.valueOf(message).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 indisponível.", ex);
        }
    }

    public String canonicalPayloadHash(String canonicalJson) {
        return sha256Hex(canonicalJson);
    }

    public String eventHash(Long tenantId,
                            Long ledgerSequence,
                            String eventType,
                            String sourceModule,
                            String sourceEntityType,
                            Long sourceEntityId,
                            String occurredAtUtc,
                            String canonicalPayloadHash,
                            String previousEventHash,
                            String keyVersion) {
        String canonical = "tenantId=" + tenantId
                + "|seq=" + ledgerSequence
                + "|eventType=" + eventType
                + "|sourceModule=" + sourceModule
                + "|sourceEntityType=" + sourceEntityType
                + "|sourceEntityId=" + sourceEntityId
                + "|occurredAt=" + occurredAtUtc
                + "|payloadHash=" + canonicalPayloadHash
                + "|previousHash=" + previousEventHash
                + "|keyVersion=" + keyVersion;
        return sha256Hex(canonical);
    }

    public Signature signEvent(String eventHash, String keyVersion, TransactionEvidenceAlgorithm algorithm) {
        if (algorithm != TransactionEvidenceAlgorithm.SHA256_HMAC) {
            return new Signature(algorithm, keyVersion, sha256Hex(eventHash));
        }
        String secret = keyProvider.hmacSecretForVersion(keyVersion)
                .orElseThrow(() -> new IllegalStateException("Transaction evidence key indisponível para keyVersion=" + keyVersion));
        return new Signature(algorithm, keyVersion, hmacSha256Hex(eventHash, secret));
    }

    public boolean verifyEventSignature(String eventHash, String signatureHex, String keyVersion, TransactionEvidenceAlgorithm algorithm) {
        if (algorithm != TransactionEvidenceAlgorithm.SHA256_HMAC) {
            return sha256Hex(eventHash).equalsIgnoreCase(signatureHex);
        }
        String secret = keyProvider.hmacSecretForVersion(keyVersion).orElse(null);
        if (secret == null) return false;
        String expected = hmacSha256Hex(eventHash, secret);
        return expected.equalsIgnoreCase(signatureHex);
    }

    public record Signature(TransactionEvidenceAlgorithm algorithm, String keyVersion, String signatureHex) {}
}

