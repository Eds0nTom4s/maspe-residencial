package com.restaurante.txevidence;

import com.restaurante.model.enums.TransactionEvidenceAlgorithm;
import com.restaurante.txevidence.hash.TransactionEvidenceHashService;
import com.restaurante.txevidence.key.TransactionEvidenceKeyProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionEvidenceHashServiceTest {

    @Test
    void hashesAndHmacAreDeterministicAndSensitiveToInput() {
        TransactionEvidenceKeyProvider keyProvider = new TransactionEvidenceKeyProvider() {
            @Override public String activeKeyVersion() { return "1"; }
            @Override public Optional<String> hmacSecretForVersion(String keyVersion) { return Optional.of("test-secret"); }
        };
        TransactionEvidenceHashService hash = new TransactionEvidenceHashService(keyProvider);

        String payloadHash1 = hash.canonicalPayloadHash("{\"a\":1}");
        String payloadHash2 = hash.canonicalPayloadHash("{\"a\":1}");
        assertThat(payloadHash1).isEqualTo(payloadHash2);

        String eventHash1 = hash.eventHash(1L, 1L, "PAYMENT_CONFIRMED", "PAYMENT", "PAGAMENTO", 10L, "2026-05-25T10:00Z", payloadHash1, "GENESIS", "1");
        String eventHash2 = hash.eventHash(1L, 1L, "PAYMENT_CONFIRMED", "PAYMENT", "PAGAMENTO", 10L, "2026-05-25T10:00Z", payloadHash1, "DIFF", "1");
        assertThat(eventHash1).isNotEqualTo(eventHash2);

        var sig = hash.signEvent(eventHash1, "1", TransactionEvidenceAlgorithm.SHA256_HMAC);
        assertThat(hash.verifyEventSignature(eventHash1, sig.signatureHex(), "1", TransactionEvidenceAlgorithm.SHA256_HMAC)).isTrue();
        assertThat(hash.verifyEventSignature(eventHash1, "deadbeef", "1", TransactionEvidenceAlgorithm.SHA256_HMAC)).isFalse();
    }
}

