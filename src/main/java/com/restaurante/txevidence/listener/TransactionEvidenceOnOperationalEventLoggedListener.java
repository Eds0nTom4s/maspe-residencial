package com.restaurante.txevidence.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.service.operacional.event.OperationalEventLoggedEvent;
import com.restaurante.txevidence.dto.TransactionEvidenceEventRequest;
import com.restaurante.txevidence.properties.TransactionEvidenceProperties;
import com.restaurante.txevidence.service.TransactionEvidenceLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEvidenceOnOperationalEventLoggedListener {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final TransactionEvidenceLedgerService ledgerService;
    private final TransactionEvidenceProperties properties;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationalEventLogged(OperationalEventLoggedEvent event) {
        if (event == null) return;
        if (!properties.isEnabled()) return;
        if (event.getTenantId() == null || event.getLogId() == null) return;
        if (event.getEventType() == null || event.getEntityType() == null || event.getEntityId() == null) return;

        // Prevent infinite recursion: ledger recording also writes OperationalEventLog entries.
        String eventTypeName = event.getEventType().name();
        if (eventTypeName.startsWith("TRANSACTION_EVIDENCE_") || eventTypeName.startsWith("TRANSACTION_LEDGER_")) return;

        TransactionEvidenceSourceModule sourceModule = mapSourceModule(eventTypeName, event.getEntityType().name());
        if (!shouldRecord(sourceModule, eventTypeName)) return;

        Map<String, Object> payloadFields = new LinkedHashMap<>();
        payloadFields.put("operationalEventType", eventTypeName);
        payloadFields.put("operationalEntityType", event.getEntityType().name());
        payloadFields.put("operationalEntityId", event.getEntityId());
        payloadFields.put("origem", event.getOrigem() != null ? event.getOrigem().name() : null);
        payloadFields.put("statusAnterior", safeTruncate(event.getStatusAnterior(), 120));
        payloadFields.put("statusNovo", safeTruncate(event.getStatusNovo(), 120));
        payloadFields.put("motivoHash", sha256HexSafe(event.getMotivo()));

        Map<String, Object> metadata = parseAndSanitizeMetadata(event.getMetadataJson());
        if (metadata != null && !metadata.isEmpty()) payloadFields.put("metadata", metadata);

        LocalDateTime occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : LocalDateTime.now();
        // Normalize to UTC in canonicalizer layer by passing an ISO string.
        payloadFields.put("occurredAtUtc", occurredAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        TransactionEvidenceEventRequest req = new TransactionEvidenceEventRequest();
        req.setTenantId(event.getTenantId());
        req.setEventType(eventTypeName);
        req.setSourceModule(sourceModule);
        req.setSourceEntityType(event.getEntityType().name());
        req.setSourceEntityId(event.getEntityId());
        req.setSourceEventId(event.getLogId());
        req.setOccurredAt(occurredAt);
        req.setIdempotencyKey(idempotencyKeyForOperationalLog(event.getTenantId(), event.getLogId()));
        req.setPayloadFields(payloadFields);
        req.setMetadataFields(Map.of("opLogId", event.getLogId()));

        try {
            ledgerService.recordEvidenceEvent(req);
        } catch (Exception ex) {
            // Do not break business flows; this listener runs AFTER_COMMIT.
            log.warn("TX evidence ledger record failed (tenantId={}, opLogId={}, eventType={}): {}",
                    event.getTenantId(), event.getLogId(), eventTypeName, ex.getMessage());
        }
    }

    static String idempotencyKeyForOperationalLog(Long tenantId, Long logId) {
        return "tenant:" + tenantId + ":oplog:" + logId + ":tx-ledger:v1";
    }

    private static boolean shouldRecord(TransactionEvidenceSourceModule module, String eventTypeName) {
        if (module == null) return false;
        // Keep MVP focused on critical domains.
        return switch (module) {
            case PAYMENT, FISCAL, INVENTORY, BILLING, EVIDENCE -> true;
            default -> false;
        };
    }

    private static TransactionEvidenceSourceModule mapSourceModule(String eventTypeName, String entityTypeName) {
        String e = (eventTypeName != null ? eventTypeName : "").toUpperCase(Locale.ROOT);
        String t = (entityTypeName != null ? entityTypeName : "").toUpperCase(Locale.ROOT);

        if (e.contains("APPYPAY") || e.startsWith("PAGAMENTO") || t.contains("PAGAMENTO")) return TransactionEvidenceSourceModule.PAYMENT;
        if (e.startsWith("FISCAL") || e.startsWith("OFFICIAL_FISCAL") || t.contains("FISCAL")) return TransactionEvidenceSourceModule.FISCAL;
        if (e.startsWith("INVENTORY") || t.contains("INVENTORY")) return TransactionEvidenceSourceModule.INVENTORY;
        if (e.startsWith("USAGE") || e.startsWith("BILLING") || e.startsWith("TENANT_BILLING")) return TransactionEvidenceSourceModule.BILLING;
        if (e.startsWith("EVIDENCE_BUNDLE") || e.startsWith("SNAPSHOT_FINANCEIRO")) return TransactionEvidenceSourceModule.EVIDENCE;
        return TransactionEvidenceSourceModule.SYSTEM;
    }

    private Map<String, Object> parseAndSanitizeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = objectMapper.readValue(metadataJson, MAP_TYPE);
            return sanitizeMap(raw, 0);
        } catch (Exception e) {
            return Map.of("metadataParseError", "invalid_json");
        }
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> raw, int depth) {
        if (raw == null || raw.isEmpty()) return Map.of();
        if (depth > 3) return Map.of("truncated", true);

        Map<String, Object> out = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> en : raw.entrySet()) {
            if (count++ >= 50) break;
            String key = safeTruncate(en.getKey(), 80);
            if (key == null) continue;
            Object val = en.getValue();

            if (looksSensitiveKey(key)) {
                out.put(key, "***");
                continue;
            }

            if (val instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m2 = (Map<String, Object>) m;
                out.put(key, sanitizeMap(m2, depth + 1));
            } else if (val instanceof String s) {
                out.put(key, safeTruncate(s, 200));
            } else if (val instanceof Number || val instanceof Boolean) {
                out.put(key, val);
            } else {
                out.put(key, safeTruncate(String.valueOf(val), 200));
            }
        }
        return out;
    }

    private static boolean looksSensitiveKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("token")
                || k.contains("secret")
                || k.contains("password")
                || k.contains("authorization")
                || k.contains("header")
                || k.contains("cookie")
                || k.contains("hmac")
                || k.contains("signature")
                || k.contains("card")
                || k.contains("pan")
                || k.contains("cvv")
                || k.contains("phone")
                || k.contains("telefone")
                || k.contains("payload");
    }

    private static String safeTruncate(String s, int maxLen) {
        if (s == null) return null;
        String v = s.strip();
        if (v.length() <= maxLen) return v;
        return v.substring(0, maxLen);
    }

    private static String sha256HexSafe(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}

