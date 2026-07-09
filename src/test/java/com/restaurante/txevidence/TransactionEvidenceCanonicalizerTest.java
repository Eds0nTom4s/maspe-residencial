package com.restaurante.txevidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.txevidence.canonical.TransactionEvidenceCanonicalizer;
import com.restaurante.txevidence.properties.TransactionEvidenceProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionEvidenceCanonicalizerTest {

    @Test
    void canonicalPayloadIsDeterministicAndNormalizesValues() throws Exception {
        TransactionEvidenceProperties props = new TransactionEvidenceProperties();
        props.setCanonicalPayloadVersion("tx-evidence-v1");

        ObjectMapper om = new ObjectMapper();
        TransactionEvidenceCanonicalizer canonicalizer = new TransactionEvidenceCanonicalizer(props, om);

        LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 25, 10, 0, 0);

        Map<String, Object> fieldsA = new HashMap<>();
        fieldsA.put("b", "x");
        fieldsA.put("a", new BigDecimal("5000.00"));
        fieldsA.put("when", occurredAt);

        Map<String, Object> fieldsB = new HashMap<>();
        fieldsB.put("when", occurredAt);
        fieldsB.put("a", new BigDecimal("5000.0"));
        fieldsB.put("b", "x");

        String jsonA = canonicalizer.canonicalize(1L, "PAYMENT_CONFIRMED", TransactionEvidenceSourceModule.PAYMENT, "PAGAMENTO", 10L, occurredAt, fieldsA)
                .toCanonicalJsonString();
        String jsonB = canonicalizer.canonicalize(1L, "PAYMENT_CONFIRMED", TransactionEvidenceSourceModule.PAYMENT, "PAGAMENTO", 10L, occurredAt, fieldsB)
                .toCanonicalJsonString();

        assertThat(jsonA).isEqualTo(jsonB);

        JsonNode root = om.readTree(jsonA);
        assertThat(root.get("schemaVersion").asText()).isEqualTo("tx-evidence-v1");
        assertThat(root.get("tenantId").asLong()).isEqualTo(1L);
        assertThat(root.get("eventType").asText()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(root.get("fields").get("a").asText()).isEqualTo("5000");
    }
}

