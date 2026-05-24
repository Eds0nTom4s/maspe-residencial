package com.restaurante.fiscal.evidence.service;

import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceByTaxRateDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceDocumentItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceSectionDTO;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalRegime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaxEvidenceService {

    private final TaxProperties props;
    private final TenantFiscalProfileRepository fiscalProfileRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentLineRepository fiscalDocumentLineRepository;

    public TaxEvidenceSectionDTO buildForTurno(Long tenantId, Long turnoId) {
        TaxEvidenceSectionDTO out = new TaxEvidenceSectionDTO();
        out.setGeneratedAt(LocalDateTime.now());
        out.setTenantId(tenantId);
        out.setTurnoId(turnoId);

        if (!props.isEnabled() || !props.getEvidence().isEnabled() || tenantId == null || turnoId == null) {
            out.setFiscalRegime(FiscalRegime.NOT_CONFIGURED.name());
            out.setTotalDocuments(0);
            out.setIssuedDocuments(0);
            out.setCancelledDocuments(0);
            out.setTaxableAmount(BigDecimal.ZERO);
            out.setExemptAmount(BigDecimal.ZERO);
            out.setTaxAmount(BigDecimal.ZERO);
            out.setGrossAmount(BigDecimal.ZERO);
            out.setByTaxRate(List.of());
            out.setWarnings(List.of("TAX_EVIDENCE_DISABLED"));
            out.setDocuments(List.of());
            return out;
        }

        TenantFiscalProfile profile = fiscalProfileRepository.findByTenantId(tenantId).orElse(null);
        out.setFiscalRegime(profile != null ? profile.getFiscalRegime().name() : FiscalRegime.NOT_CONFIGURED.name());

        List<FiscalDocument> docs = fiscalDocumentRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId);

        int total = docs.size();
        int issued = (int) docs.stream().filter(d -> d.getStatus() == FiscalDocumentStatus.ISSUED).count();
        int cancelled = (int) docs.stream().filter(d -> d.getStatus() == FiscalDocumentStatus.CANCELLED || d.getStatus() == FiscalDocumentStatus.VOIDED).count();

        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;

        Map<String, Agg> byRate = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<TaxEvidenceDocumentItemDTO> items = new ArrayList<>();

        if (profile == null || profile.getFiscalRegime() == FiscalRegime.NOT_CONFIGURED) {
            warnings.add("TENANT_FISCAL_PROFILE_NOT_CONFIGURED");
        }
        if (profile != null && !profile.isFiscalDocumentEnabled()) {
            warnings.add("FISCAL_DOCUMENT_DISABLED");
        }

        for (FiscalDocument d : docs) {
            if (d == null) continue;
            taxable = taxable.add(nz(d.getTaxableAmount()));
            exempt = exempt.add(nz(d.getExemptAmount()));
            tax = tax.add(nz(d.getTaxAmount()));
            gross = gross.add(nz(d.getTotalAmount()));

            List<FiscalDocumentLine> lines = fiscalDocumentLineRepository.findByTenantIdAndFiscalDocumentId(tenantId, d.getId());
            String docHash = hashForDocument(d, lines);

            TaxEvidenceDocumentItemDTO it = new TaxEvidenceDocumentItemDTO();
            it.setDocumentId(d.getId());
            it.setDocumentType(d.getDocumentType() != null ? d.getDocumentType().name() : null);
            it.setStatus(d.getStatus() != null ? d.getStatus().name() : null);
            it.setDocumentNumber(d.getDocumentNumber());
            it.setSeries(d.getSeries());
            it.setIssuedAt(d.getIssuedAt());
            it.setPedidoId(d.getPedido() != null ? d.getPedido().getId() : null);
            it.setPagamentoId(d.getPagamento() != null ? d.getPagamento().getId() : null);
            it.setSessaoConsumoId(d.getSessaoConsumo() != null ? d.getSessaoConsumo().getId() : null);
            it.setSubtotalAmount(d.getSubtotalAmount());
            it.setTaxableAmount(d.getTaxableAmount());
            it.setExemptAmount(d.getExemptAmount());
            it.setTaxAmount(d.getTaxAmount());
            it.setTotalAmount(d.getTotalAmount());
            it.setDocumentHash(docHash);
            items.add(it);

            if (lines.isEmpty()) warnings.add("DOCUMENT_WITHOUT_LINES");
            if (d.getTaxAmount() != null && d.getTaxAmount().compareTo(BigDecimal.ZERO) == 0) warnings.add("DOCUMENT_WITH_ZERO_TAX");

            for (FiscalDocumentLine l : lines) {
                String code = l.getTaxRateCode() != null ? l.getTaxRateCode() : "UNKNOWN";
                Agg a = byRate.computeIfAbsent(code, k -> new Agg());
                a.documents.add(d.getId());
                a.taxable = a.taxable.add(nz(l.getNetAmount()));
                if (l.getTaxAmount() == null || l.getTaxAmount().compareTo(BigDecimal.ZERO) == 0) {
                    a.exempt = a.exempt.add(nz(l.getNetAmount()));
                } else {
                    a.tax = a.tax.add(nz(l.getTaxAmount()));
                }
                a.gross = a.gross.add(nz(l.getGrossAmount()));
            }
        }

        out.setTotalDocuments(total);
        out.setIssuedDocuments(issued);
        out.setCancelledDocuments(cancelled);
        out.setTaxableAmount(taxable);
        out.setExemptAmount(exempt);
        out.setTaxAmount(tax);
        out.setGrossAmount(gross);
        out.setWarnings(warnings.stream().distinct().toList());
        out.setDocuments(items);

        List<TaxEvidenceByTaxRateDTO> byRateList = new ArrayList<>();
        for (var e : byRate.entrySet()) {
            Agg a = e.getValue();
            TaxEvidenceByTaxRateDTO dto = new TaxEvidenceByTaxRateDTO();
            dto.setTaxRateCode(e.getKey());
            dto.setTaxableAmount(a.taxable);
            dto.setExemptAmount(a.exempt);
            dto.setTaxAmount(a.tax);
            dto.setGrossAmount(a.gross);
            dto.setDocumentsCount(a.documents.size());
            byRateList.add(dto);
        }
        byRateList.sort(java.util.Comparator.comparing(TaxEvidenceByTaxRateDTO::getTaxRateCode));
        out.setByTaxRate(byRateList);

        return out;
    }

    private String hashForDocument(FiscalDocument d, List<FiscalDocumentLine> lines) {
        String canonical = canonicalString(d, lines);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private String canonicalString(FiscalDocument d, List<FiscalDocumentLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("documentId=").append(v(d != null ? d.getId() : null))
                .append("|tenantId=").append(v(d != null && d.getTenant() != null ? d.getTenant().getId() : null))
                .append("|type=").append(d != null && d.getDocumentType() != null ? d.getDocumentType().name() : "null")
                .append("|status=").append(d != null && d.getStatus() != null ? d.getStatus().name() : "null")
                .append("|number=").append(v(d != null ? d.getDocumentNumber() : null))
                .append("|series=").append(v(d != null ? d.getSeries() : null))
                .append("|issuedAt=").append(v(d != null ? d.getIssuedAt() : null))
                .append("|fiscalRegime=").append(d != null && d.getFiscalRegime() != null ? d.getFiscalRegime().name() : "null")
                .append("|taxable=").append(money(d != null ? d.getTaxableAmount() : null))
                .append("|exempt=").append(money(d != null ? d.getExemptAmount() : null))
                .append("|tax=").append(money(d != null ? d.getTaxAmount() : null))
                .append("|total=").append(money(d != null ? d.getTotalAmount() : null));

        if (lines != null && !lines.isEmpty()) {
            lines.stream()
                    .sorted(java.util.Comparator.comparing(l -> l.getId() != null ? l.getId() : Long.MAX_VALUE))
                    .forEach(l -> sb.append("|line=").append(lineCanonical(l)));
        }
        return sb.toString();
    }

    private String lineCanonical(FiscalDocumentLine l) {
        return "desc=" + v(l != null ? l.getDescription() : null)
                + ",qty=" + v(l != null ? l.getQuantity() : null)
                + ",unit=" + money(l != null ? l.getUnitPrice() : null)
                + ",net=" + money(l != null ? l.getNetAmount() : null)
                + ",rateCode=" + v(l != null ? l.getTaxRateCode() : null)
                + ",rateVal=" + moneyRate(l != null ? l.getTaxRateValue() : null)
                + ",tax=" + money(l != null ? l.getTaxAmount() : null)
                + ",gross=" + money(l != null ? l.getGrossAmount() : null)
                + ",cat=" + (l != null && l.getTaxCategory() != null ? l.getTaxCategory().name() : "null")
                + ",exemptReasonPresent=" + (l != null && l.getExemptReason() != null && !l.getExemptReason().isBlank());
    }

    private static String v(Object o) {
        return o == null ? "null" : String.valueOf(o);
    }

    private String money(BigDecimal v) {
        if (v == null) return "null";
        try {
            return v.setScale(props.getMonetaryScale(), props.getRoundingMode()).toPlainString();
        } catch (Exception e) {
            return v.toPlainString();
        }
    }

    private static String moneyRate(BigDecimal v) {
        if (v == null) return "null";
        try {
            return v.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return v.toPlainString();
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static final class Agg {
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        java.util.Set<Long> documents = new java.util.HashSet<>();
    }
}

