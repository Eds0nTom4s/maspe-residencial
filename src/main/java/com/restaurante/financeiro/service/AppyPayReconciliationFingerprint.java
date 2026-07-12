package com.restaurante.financeiro.service;

import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class AppyPayReconciliationFingerprint {

    private AppyPayReconciliationFingerprint() {
    }

    static String sha256(AppyPayChargeResponse response) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload(response).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    static String canonicalPayload(AppyPayChargeResponse response) {
        if (response == null) {
            return "appypay-reconciliation:v1|null";
        }
        return "appypay-reconciliation:v1"
                + field("chargeId", response.getChargeId())
                + field("merchantTransactionId", response.getMerchantTransactionId())
                + field("status", normalize(response.getStatus()))
                + field("amount", response.getAmount())
                + field("paymentMethod", normalize(response.getPaymentMethod()))
                + field("entity", response.getEntity())
                + field("reference", response.getReference())
                + field("errorMessage", response.getErrorMessage());
    }

    private static String field(String name, Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return "|" + name.length() + ":" + name + "=" + text.length() + ":" + text;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
