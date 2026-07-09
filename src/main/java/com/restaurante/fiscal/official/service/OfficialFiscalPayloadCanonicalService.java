package com.restaurante.fiscal.official.service;

import com.restaurante.fiscal.official.dto.OfficialFiscalDocumentPayload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class OfficialFiscalPayloadCanonicalService {

    public String canonicalString(OfficialFiscalDocumentPayload p) {
        StringBuilder sb = new StringBuilder();
        sb.append("authority=").append(v(p != null ? p.getAuthority() : null))
                .append("|country=").append(v(p != null ? p.getCountryCode() : null))
                .append("|taxpayer=").append(v(p != null ? p.getTaxpayerNumber() : null))
                .append("|softwareCert=").append(v(p != null ? p.getSoftwareCertificateId() : null))
                .append("|docType=").append(v(p != null ? p.getDocumentType() : null))
                .append("|docNumber=").append(v(p != null ? p.getDocumentNumber() : null))
                .append("|series=").append(v(p != null ? p.getSeries() : null))
                .append("|issuedAt=").append(v(p != null ? p.getIssuedAt() : null))
                .append("|fiscalRegime=").append(v(p != null ? p.getFiscalRegime() : null))
                .append("|currency=").append(v(p != null ? p.getCurrency() : null));

        if (p != null && p.getCustomer() != null) {
            sb.append("|custName=").append(v(p.getCustomer().getName()))
                    .append("|custTaxpayer=").append(v(p.getCustomer().getTaxpayerNumber()))
                    .append("|custCountry=").append(v(p.getCustomer().getCountryCode()));
        } else {
            sb.append("|custName=null|custTaxpayer=null|custCountry=null");
        }

        if (p != null && p.getTotals() != null) {
            sb.append("|taxable=").append(money(p.getTotals().getTaxableAmount()))
                    .append("|exempt=").append(money(p.getTotals().getExemptAmount()))
                    .append("|tax=").append(money(p.getTotals().getTaxAmount()))
                    .append("|total=").append(money(p.getTotals().getTotalAmount()));
        } else {
            sb.append("|taxable=0.00|exempt=0.00|tax=0.00|total=0.00");
        }

        if (p != null && p.getCorrectionInfo() != null) {
            sb.append("|origDocId=").append(v(p.getCorrectionInfo().getOriginalDocumentId()))
                    .append("|origDocNumber=").append(v(p.getCorrectionInfo().getOriginalDocumentNumber()))
                    .append("|correctionType=").append(v(p.getCorrectionInfo().getCorrectionType()))
                    .append("|correctionReasonHash=").append(hashText(p.getCorrectionInfo().getCorrectionReason()));
        } else {
            sb.append("|origDocId=null|origDocNumber=null|correctionType=null|correctionReasonHash=").append(hashText(null));
        }

        if (p != null && p.getLines() != null && !p.getLines().isEmpty()) {
            for (int i = 0; i < p.getLines().size(); i++) {
                var l = p.getLines().get(i);
                sb.append("|line[").append(i).append("]=")
                        .append(v(l != null ? l.getDescription() : null))
                        .append(",qty=").append(v(l != null ? l.getQuantity() : null))
                        .append(",unit=").append(money(l != null ? l.getUnitPrice() : null))
                        .append(",net=").append(money(l != null ? l.getNetAmount() : null))
                        .append(",rateCode=").append(v(l != null ? l.getTaxRateCode() : null))
                        .append(",rateVal=").append(money(l != null ? l.getTaxRateValue() : null))
                        .append(",tax=").append(money(l != null ? l.getTaxAmount() : null))
                        .append(",gross=").append(money(l != null ? l.getGrossAmount() : null))
                        .append(",cat=").append(v(l != null ? l.getTaxCategory() : null))
                        .append(",exReasonHash=").append(hashText(l != null ? l.getExemptReason() : null));
            }
        }

        sb.append("|sourceVersion=").append(v(p != null ? p.getSourceVersion() : null));
        return sb.toString();
    }

    public String sha256Hex(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((canonical != null ? canonical : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    public String hashText(String text) {
        String t = text != null ? text.replaceAll("[\\r\\n\\t]", " ").trim() : "";
        if (t.length() > 500) t = t.substring(0, 500);
        return sha256Hex(t);
    }

    private static String v(Object o) {
        return o == null ? "null" : String.valueOf(o);
    }

    private static String v(LocalDateTime dt) {
        return dt == null ? "null" : dt.toString();
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

