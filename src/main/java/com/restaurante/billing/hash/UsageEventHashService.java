package com.restaurante.billing.hash;

import com.restaurante.model.entity.UsageEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class UsageEventHashService {

    public String hash(UsageEvent e) {
        if (e == null) return null;
        String canonical = canonicalString(e);
        return sha256Hex(canonical);
    }

    private String canonicalString(UsageEvent e) {
        StringBuilder sb = new StringBuilder();
        sb.append("tenantId=").append(e.getTenant() != null ? e.getTenant().getId() : null);
        sb.append("|metricCode=").append(e.getMetricCode() != null ? e.getMetricCode().name() : null);
        sb.append("|sourceEntityType=").append(e.getSourceEntityType());
        sb.append("|sourceEntityId=").append(e.getSourceEntityId());
        sb.append("|idempotencyKey=").append(e.getIdempotencyKey());
        sb.append("|occurredAt=").append(e.getOccurredAt());
        sb.append("|quantity=").append(e.getQuantity());
        sb.append("|amount=").append(e.getAmount());
        sb.append("|currency=").append(e.getCurrency());
        return sb.toString();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }
}

