package com.restaurante.billing.hash;

import com.restaurante.model.entity.UsageAggregation;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class UsageAggregationHashService {

    public String hash(UsageAggregation a) {
        if (a == null) return null;
        String canonical = canonicalString(a);
        return sha256Hex(canonical);
    }

    private String canonicalString(UsageAggregation a) {
        StringBuilder sb = new StringBuilder();
        sb.append("tenantId=").append(a.getTenant() != null ? a.getTenant().getId() : null);
        sb.append("|subscriptionId=").append(a.getSubscription() != null ? a.getSubscription().getId() : null);
        sb.append("|metricCode=").append(a.getMetricCode() != null ? a.getMetricCode().name() : null);
        sb.append("|periodStart=").append(a.getPeriodStart());
        sb.append("|periodEnd=").append(a.getPeriodEnd());
        sb.append("|quantityTotal=").append(a.getQuantityTotal());
        sb.append("|includedQuantity=").append(a.getIncludedQuantity());
        sb.append("|overageQuantity=").append(a.getOverageQuantity());
        sb.append("|calculatedChargeAmount=").append(a.getCalculatedChargeAmount());
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

