package com.restaurante.fiscal.official.service;

import com.restaurante.fiscal.official.dto.OfficialFiscalDocumentPayload;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.TenantOfficialFiscalProfile;
import com.restaurante.model.enums.FiscalDocumentType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OfficialFiscalDocumentMapper {

    public OfficialFiscalDocumentPayload mapFromFiscalDocument(FiscalDocument d,
                                                              List<FiscalDocumentLine> lines,
                                                              TenantOfficialFiscalProfile profile) {
        OfficialFiscalDocumentPayload out = new OfficialFiscalDocumentPayload();
        out.setAuthority(profile != null && profile.getAuthority() != null ? profile.getAuthority().name() : "AGT_AO");
        out.setCountryCode(profile != null ? profile.getCountryCode() : "AO");
        out.setTaxpayerNumber(profile != null ? profile.getTaxpayerNumber() : null);
        out.setSoftwareCertificateId(profile != null ? profile.getSoftwareCertificateId() : null);

        out.setDocumentType(mapType(d != null ? d.getDocumentType() : null));
        out.setDocumentNumber(d != null ? d.getDocumentNumber() : null);
        out.setSeries(d != null ? d.getSeries() : null);
        out.setIssuedAt(d != null ? d.getIssuedAt() : null);
        out.setFiscalRegime(d != null && d.getFiscalRegime() != null ? d.getFiscalRegime().name() : null);
        out.setCurrency(d != null ? d.getCurrency() : null);

        var cust = new OfficialFiscalDocumentPayload.OfficialFiscalCustomerDTO();
        cust.setName(d != null ? d.getCustomerName() : null);
        cust.setTaxpayerNumber(d != null ? d.getCustomerTaxpayerNumber() : null);
        cust.setCountryCode(out.getCountryCode());
        out.setCustomer(cust);

        var totals = new OfficialFiscalDocumentPayload.OfficialFiscalTotalsDTO();
        totals.setTaxableAmount(d != null ? d.getTaxableAmount() : null);
        totals.setExemptAmount(d != null ? d.getExemptAmount() : null);
        totals.setTaxAmount(d != null ? d.getTaxAmount() : null);
        totals.setTotalAmount(d != null ? d.getTotalAmount() : null);
        out.setTotals(totals);

        List<OfficialFiscalDocumentPayload.OfficialFiscalLineDTO> payloadLines = new ArrayList<>();
        if (lines != null) {
            for (FiscalDocumentLine l : lines) {
                OfficialFiscalDocumentPayload.OfficialFiscalLineDTO pl = new OfficialFiscalDocumentPayload.OfficialFiscalLineDTO();
                pl.setDescription(l != null ? l.getDescription() : null);
                pl.setQuantity(l != null ? l.getQuantity() : null);
                pl.setUnitPrice(l != null ? l.getUnitPrice() : null);
                pl.setNetAmount(l != null ? l.getNetAmount() : null);
                pl.setTaxRateCode(l != null ? l.getTaxRateCode() : null);
                pl.setTaxRateValue(l != null ? l.getTaxRateValue() : null);
                pl.setTaxAmount(l != null ? l.getTaxAmount() : null);
                pl.setGrossAmount(l != null ? l.getGrossAmount() : null);
                pl.setTaxCategory(l != null && l.getTaxCategory() != null ? l.getTaxCategory().name() : null);
                pl.setExemptReason(l != null ? l.getExemptReason() : null);
                payloadLines.add(pl);
            }
        }
        out.setLines(payloadLines);

        if (d != null && d.getOriginalFiscalDocument() != null) {
            var corr = new OfficialFiscalDocumentPayload.OfficialFiscalCorrectionInfoDTO();
            corr.setOriginalDocumentId(d.getOriginalFiscalDocument().getId());
            corr.setOriginalDocumentNumber(d.getOriginalFiscalDocument().getDocumentNumber());
            corr.setCorrectionReason(d.getCorrectionReason());
            corr.setCorrectionType(d.getDocumentType() != null ? d.getDocumentType().name() : null);
            out.setCorrectionInfo(corr);
        }

        out.setSourceVersion("official-fiscal-p45-v1");
        return out;
    }

    private static String mapType(FiscalDocumentType t) {
        if (t == null) return "UNKNOWN";
        if (t == FiscalDocumentType.INTERNAL_CREDIT_NOTE) return "CREDIT_NOTE_INTERNAL";
        if (t == FiscalDocumentType.INTERNAL_DEBIT_NOTE) return "DEBIT_NOTE_INTERNAL";
        if (t == FiscalDocumentType.INTERNAL_INVOICE || t == FiscalDocumentType.INTERNAL_INVOICE_RECEIPT) return "INVOICE_INTERNAL";
        return "RECEIPT_INTERNAL";
    }
}

