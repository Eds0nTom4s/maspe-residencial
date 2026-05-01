package com.restaurante.financeiro.gateway.appypay;

import java.util.Map;

public final class AppyPayWebhookPayload {

    private AppyPayWebhookPayload() {}

    public static String extractTransactionId(Map<String, Object> payload) {
        return first(payload, "id", "paymentId", "transaction_id", "chargeId");
    }

    public static String extractMerchantTransactionId(Map<String, Object> payload) {
        return first(payload, "merchantTransactionId", "merchant_transaction_id");
    }

    public static String extractReference(Map<String, Object> payload) {
        String direct = first(payload, "referencia", "referenceNumber");
        if (direct != null) return direct;

        Object rootReference = payload.get("reference");
        String rootReferenceValue = extractReferenceNumber(rootReference);
        if (rootReferenceValue != null) return rootReferenceValue;

        Object responseStatus = payload.get("responseStatus");
        if (responseStatus instanceof Map<?, ?> rs) {
            return extractReferenceNumber(rs.get("reference"));
        }

        return first(payload, "reference");
    }

    public static String extractStatus(Map<String, Object> payload) {
        String direct = first(payload, "status");
        if (direct != null) return direct;

        Object responseStatus = payload.get("responseStatus");
        if (responseStatus instanceof Map<?, ?> rs) {
            Object status = rs.get("status");
            return status != null ? status.toString() : null;
        }
        return null;
    }

    public static String extractAmount(Map<String, Object> payload) {
        return first(payload, "amount", "valor", "value");
    }

    public static boolean hasIdentifier(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return false;
        return extractTransactionId(payload) != null
                || extractMerchantTransactionId(payload) != null
                || extractReference(payload) != null;
    }

    private static String first(Map<String, Object> payload, String... names) {
        if (payload == null) return null;
        for (String name : names) {
            Object value = payload.get(name);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private static String extractReferenceNumber(Object value) {
        if (value instanceof String text && !text.isBlank()) return text;
        if (value instanceof Map<?, ?> map) {
            Object referenceNumber = map.get("referenceNumber");
            if (referenceNumber != null && !referenceNumber.toString().isBlank()) {
                return referenceNumber.toString();
            }
        }
        return null;
    }
}
