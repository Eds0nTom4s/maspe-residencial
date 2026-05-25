package com.restaurante.billing.hash;

import com.restaurante.model.entity.TenantBillingPayment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class TenantBillingPaymentHashService {

    public String hash(TenantBillingPayment p) {
        if (p == null) return null;
        return sha256Hex(canonicalString(p));
    }

    private String canonicalString(TenantBillingPayment p) {
        StringBuilder sb = new StringBuilder();
        sb.append("tenantId=").append(p.getTenant() != null ? p.getTenant().getId() : null);
        sb.append("|invoiceId=").append(p.getInvoice() != null ? p.getInvoice().getId() : null);
        sb.append("|paymentId=").append(p.getId());
        sb.append("|paymentNumber=").append(p.getPaymentNumber());
        sb.append("|status=").append(p.getStatus() != null ? p.getStatus().name() : null);
        sb.append("|method=").append(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null);
        sb.append("|amount=").append(p.getAmount());
        sb.append("|currency=").append(p.getCurrency());
        sb.append("|paidAt=").append(p.getPaidAt());
        sb.append("|confirmedAt=").append(p.getConfirmedAt());
        sb.append("|referenceHash=").append(p.getReference() != null ? sha256Hex(p.getReference()) : null);
        sb.append("|proofReferenceHash=").append(p.getProofReference() != null ? sha256Hex(p.getProofReference()) : null);
        return sb.toString();
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

