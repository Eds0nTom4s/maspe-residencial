package com.restaurante.financeiro.snapshot;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class SnapshotSignatureService {

    public String signSnapshotHash(String snapshotHash, String secret) {
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

    public boolean verifySnapshotHashSignature(String snapshotHash, String signatureHex, String secret) {
        if (signatureHex == null || signatureHex.isBlank()) return false;
        String expected = signSnapshotHash(snapshotHash, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHex.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

