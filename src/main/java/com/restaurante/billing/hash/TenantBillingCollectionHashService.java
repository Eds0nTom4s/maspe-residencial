package com.restaurante.billing.hash;

import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingSuspensionMode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class TenantBillingCollectionHashService {

    public String hash(Long tenantId,
                       TenantBillingCollectionStatus status,
                       Integer overdueInvoices,
                       java.math.BigDecimal totalOutstandingAmount,
                       LocalDateTime gracePeriodEndsAt,
                       TenantBillingSuspensionMode suspensionMode) {
        String canonical = "tenantId=" + tenantId
                + "|status=" + (status != null ? status.name() : null)
                + "|overdueInvoices=" + overdueInvoices
                + "|totalOutstandingAmount=" + totalOutstandingAmount
                + "|gracePeriodEndsAt=" + gracePeriodEndsAt
                + "|suspensionMode=" + (suspensionMode != null ? suspensionMode.name() : null);
        return sha256Hex(canonical);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(String.valueOf(input).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }
}

