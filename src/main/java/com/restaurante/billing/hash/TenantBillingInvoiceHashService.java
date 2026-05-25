package com.restaurante.billing.hash;

import com.restaurante.model.entity.TenantBillingInvoice;
import com.restaurante.model.entity.TenantBillingInvoiceLine;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class TenantBillingInvoiceHashService {

    public String hash(TenantBillingInvoice invoice, List<TenantBillingInvoiceLine> lines) {
        if (invoice == null) return null;
        String canonical = canonicalString(invoice, lines);
        return sha256Hex(canonical);
    }

    private String canonicalString(TenantBillingInvoice inv, List<TenantBillingInvoiceLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("tenantId=").append(inv.getTenant() != null ? inv.getTenant().getId() : null);
        sb.append("|invoiceNumber=").append(inv.getInvoiceNumber());
        sb.append("|status=").append(inv.getStatus() != null ? inv.getStatus().name() : null);
        sb.append("|subtotal=").append(inv.getSubtotalAmount());
        sb.append("|discount=").append(inv.getDiscountAmount());
        sb.append("|tax=").append(inv.getTaxAmount());
        sb.append("|total=").append(inv.getTotalAmount());
        sb.append("|issuedAt=").append(inv.getIssuedAt());

        if (lines != null) {
            for (TenantBillingInvoiceLine l : lines) {
                if (l == null) continue;
                sb.append("|line{");
                sb.append("metricCode=").append(l.getMetricCode() != null ? l.getMetricCode().name() : null);
                sb.append(",qty=").append(l.getQuantity());
                sb.append(",unitPrice=").append(l.getUnitPrice());
                sb.append(",amount=").append(l.getAmount());
                sb.append(",included=").append(l.getIncludedQuantity());
                sb.append(",overage=").append(l.getOverageQuantity());
                sb.append(",periodStart=").append(l.getPeriodStart());
                sb.append(",periodEnd=").append(l.getPeriodEnd());
                sb.append("}");
            }
        }
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

