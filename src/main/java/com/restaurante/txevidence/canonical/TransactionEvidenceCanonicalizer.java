package com.restaurante.txevidence.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.txevidence.properties.TransactionEvidenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class TransactionEvidenceCanonicalizer {

    private final TransactionEvidenceProperties props;
    private final ObjectMapper objectMapper;

    public CanonicalPayload canonicalize(Long tenantId,
                                         String eventType,
                                         TransactionEvidenceSourceModule sourceModule,
                                         String sourceEntityType,
                                         Long sourceEntityId,
                                         LocalDateTime occurredAt,
                                         Map<String, Object> payloadFields) {
        ObjectMapper om = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        ObjectNode root = om.createObjectNode();
        root.put("schemaVersion", props.getCanonicalPayloadVersion());
        root.put("tenantId", tenantId);
        root.put("eventType", eventType);
        root.put("sourceModule", sourceModule != null ? sourceModule.name() : null);
        root.put("sourceEntityType", sourceEntityType);
        root.put("sourceEntityId", sourceEntityId);
        root.put("occurredAt", toUtcIso(occurredAt));

        if (payloadFields != null && !payloadFields.isEmpty()) {
            ObjectNode fields = om.createObjectNode();
            Map<String, Object> sorted = new TreeMap<>(payloadFields);
            for (var e : sorted.entrySet()) {
                if (e.getKey() == null) continue;
                Object v = normalizeValue(e.getValue());
                fields.set(e.getKey(), om.valueToTree(v));
            }
            root.set("fields", fields);
        }

        return new CanonicalPayload(root, om);
    }

    private Object normalizeValue(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
        if (v instanceof LocalDateTime ldt) return toUtcIso(ldt);
        if (v instanceof Instant ins) return ins.atOffset(ZoneOffset.UTC).toString();
        return v;
    }

    private String toUtcIso(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.atOffset(ZoneOffset.UTC).toString();
    }

    public record CanonicalPayload(ObjectNode json, ObjectMapper mapper) {
        public String toCanonicalJsonString() {
            try {
                return mapper.writeValueAsString(json);
            } catch (Exception ex) {
                throw new IllegalStateException("Falha ao gerar canonical JSON.", ex);
            }
        }
    }
}

