package com.restaurante.service.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.response.SyncErrorResponse;
import com.restaurante.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class DeviceSyncCursorService {

    private final ObjectMapper objectMapper;

    @Value("${consuma.sync.cursor.hmac-secret:dev-sync-cursor-secret-change-me}")
    private String cursorHmacSecret;

    @Value("${consuma.sync.cursor.require-signature:true}")
    private boolean requireSignature;

    /**
     * Cursor format (v1):
     *   base64url(jsonPayload) + "." + base64url(HMAC_SHA256(base64url(jsonPayload), secret))
     *
     * Legacy format (v0) optionally supported:
     *   base64url(jsonPayload)
     */
    public String encode(Object cursorPayload) {
        try {
            String json = objectMapper.writeValueAsString(cursorPayload);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
            String sigB64 = sign(payloadB64);
            return payloadB64 + "." + sigB64;
        } catch (Exception e) {
            throw new SyncCursorException(SyncErrorResponse.SyncErrorCode.SYNC_CURSOR_INVALID, "Cursor de sincronização inválido.");
        }
    }

    public <T> T decode(String cursor, Class<T> type) {
        if (cursor == null || cursor.isBlank()) return null;

        String payloadB64;
        String signatureB64 = null;

        int dot = cursor.indexOf('.');
        if (dot >= 0) {
            payloadB64 = cursor.substring(0, dot);
            signatureB64 = cursor.substring(dot + 1);
        } else {
            payloadB64 = cursor;
        }

        if (requireSignature) {
            if (signatureB64 == null || signatureB64.isBlank()) {
                throw new SyncCursorException(SyncErrorResponse.SyncErrorCode.SYNC_CURSOR_INVALID, "Cursor de sincronização inválido.");
            }
            if (!verify(payloadB64, signatureB64)) {
                throw new SyncCursorException(SyncErrorResponse.SyncErrorCode.SYNC_CURSOR_INVALID_SIGNATURE, "Cursor de sincronização inválido.");
            }
        } else {
            // allow legacy v0 when requireSignature=false
            if (signatureB64 != null && !signatureB64.isBlank() && !verify(payloadB64, signatureB64)) {
                throw new SyncCursorException(SyncErrorResponse.SyncErrorCode.SYNC_CURSOR_INVALID_SIGNATURE, "Cursor de sincronização inválido.");
            }
        }

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payloadB64);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, type);
        } catch (SyncCursorException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncCursorException(SyncErrorResponse.SyncErrorCode.SYNC_CURSOR_MALFORMED, "Cursor de sincronização inválido.");
        }
    }

    private String sign(String payloadB64) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cursorHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new BusinessException("Falha ao assinar cursor.");
        }
    }

    private boolean verify(String payloadB64, String signatureB64) {
        try {
            String expected = sign(payloadB64);
            return constantTimeEquals(expected, signatureB64);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= (x[i] ^ y[i]);
        }
        return r == 0;
    }

    public static class SyncCursorException extends RuntimeException {
        private final SyncErrorResponse.SyncErrorCode code;

        public SyncCursorException(SyncErrorResponse.SyncErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        public SyncErrorResponse.SyncErrorCode getCode() {
            return code;
        }
    }
}

