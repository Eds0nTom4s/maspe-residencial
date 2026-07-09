package com.restaurante.fiscal.evidence.service;

import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.autoissue.repository.FiscalAutoIssueJobRepository;
import com.restaurante.fiscal.repository.FiscalAdjustmentAssessmentRepository;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.fiscal.official.config.OfficialFiscalProperties;
import com.restaurante.fiscal.official.repository.OfficialFiscalSubmissionRepository;
import com.restaurante.fiscal.official.repository.TenantOfficialFiscalProfileRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceAssessmentItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceByTaxRateDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceCorrectionDocumentItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceDocumentItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.TaxEvidenceSectionDTO;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.OfficialFiscalSubmission;
import com.restaurante.model.entity.TenantFiscalProfile;
import com.restaurante.model.entity.TenantOfficialFiscalProfile;
import com.restaurante.model.enums.FiscalAdjustmentAssessmentStatus;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import com.restaurante.model.enums.OfficialFiscalSubmissionStatus;
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
    private final OfficialFiscalProperties officialProps;
    private final TenantFiscalProfileRepository fiscalProfileRepository;
    private final TenantOfficialFiscalProfileRepository officialProfileRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentLineRepository fiscalDocumentLineRepository;
    private final FiscalAutoIssueJobRepository autoIssueJobRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final FiscalAdjustmentAssessmentRepository assessmentRepository;
    private final OfficialFiscalSubmissionRepository officialSubmissionRepository;

    public TaxEvidenceSectionDTO buildForTurno(Long tenantId, Long turnoId) {
        TaxEvidenceSectionDTO out = new TaxEvidenceSectionDTO();
        out.setGeneratedAt(LocalDateTime.now());
        out.setTenantId(tenantId);
        out.setTurnoId(turnoId);
        out.setAutoIssueEnabled(props.getDocument().getAutoIssue().isEnabled());
        out.setAutoIssueOnPayment(props.getDocument().isAutoIssueOnPayment());

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

        List<FiscalDocument> allDocs = fiscalDocumentRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId);
        List<FiscalDocument> corrections = allDocs.stream()
                .filter(d -> d != null && (d.getDocumentType() == FiscalDocumentType.INTERNAL_CREDIT_NOTE || d.getDocumentType() == FiscalDocumentType.INTERNAL_DEBIT_NOTE))
                .toList();
        List<FiscalDocument> docs = allDocs.stream()
                .filter(d -> d != null && d.getDocumentType() != FiscalDocumentType.INTERNAL_CREDIT_NOTE && d.getDocumentType() != FiscalDocumentType.INTERNAL_DEBIT_NOTE)
                .toList();

        List<OfficialFiscalSubmission> officialSubmissions = officialProps.isEnabled()
                ? officialSubmissionRepository.listByTurno(tenantId, turnoId)
                : List.of();
        Map<Long, OfficialFiscalSubmission> submissionByDocumentId = new HashMap<>();
        for (OfficialFiscalSubmission s : officialSubmissions) {
            if (s != null && s.getFiscalDocument() != null && s.getFiscalDocument().getId() != null) {
                submissionByDocumentId.put(s.getFiscalDocument().getId(), s);
            }
        }

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
        List<TaxEvidenceCorrectionDocumentItemDTO> correctionItems = new ArrayList<>();
        List<TaxEvidenceAssessmentItemDTO> assessmentItems = new ArrayList<>();

        BigDecimal correctionNet = BigDecimal.ZERO;
        BigDecimal correctionTax = BigDecimal.ZERO;
        BigDecimal correctionGross = BigDecimal.ZERO;

        if (profile == null || profile.getFiscalRegime() == FiscalRegime.NOT_CONFIGURED) {
            warnings.add("TENANT_FISCAL_PROFILE_NOT_CONFIGURED");
        }
        if (profile != null && !profile.isFiscalDocumentEnabled()) {
            warnings.add("FISCAL_DOCUMENT_DISABLED");
        }

        // Prompt 43.1: métricas de auto-emissão (jobs + pagamentos confirmados sem documento)
        try {
            Map<FiscalAutoIssueJobStatus, Integer> statusCounts = new HashMap<>();
            for (Object[] row : autoIssueJobRepository.countByTenantAndTurnoGroupByStatus(tenantId, turnoId)) {
                if (row == null || row.length < 2) continue;
                FiscalAutoIssueJobStatus st = (FiscalAutoIssueJobStatus) row[0];
                Long cnt = (Long) row[1];
                statusCounts.put(st, cnt != null ? cnt.intValue() : 0);
            }
            int totalJobs = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
            out.setTotalAutoIssueJobs(totalJobs);
            out.setPendingAutoIssueJobs(statusCounts.getOrDefault(FiscalAutoIssueJobStatus.PENDING, 0));
            out.setFailedRetryableAutoIssueJobs(statusCounts.getOrDefault(FiscalAutoIssueJobStatus.FAILED_RETRYABLE, 0));
            out.setFailedPermanentAutoIssueJobs(statusCounts.getOrDefault(FiscalAutoIssueJobStatus.FAILED_PERMANENT, 0));
            out.setSkippedAutoIssueJobs(statusCounts.getOrDefault(FiscalAutoIssueJobStatus.SKIPPED, 0));
            out.setIssuedByAutoIssueJobs(statusCounts.getOrDefault(FiscalAutoIssueJobStatus.ISSUED, 0));

            long confirmedWithoutDoc = pagamentoGatewayRepository.countConfirmedPaymentsWithoutIssuedFiscalDocument(tenantId, turnoId);
            out.setConfirmedPaymentsWithoutFiscalDocument((int) confirmedWithoutDoc);

            if (confirmedWithoutDoc > 0) {
                warnings.add("CONFIRMED_PAYMENT_WITHOUT_FISCAL_DOCUMENT");
            }
            if (out.getFailedPermanentAutoIssueJobs() != null && out.getFailedPermanentAutoIssueJobs() > 0) {
                warnings.add("AUTO_ISSUE_JOB_FAILED_PERMANENT");
            }
            if (out.getFailedRetryableAutoIssueJobs() != null && out.getFailedRetryableAutoIssueJobs() > 0) {
                warnings.add("AUTO_ISSUE_JOB_FAILED_RETRYABLE");
            }
        } catch (Exception ignored) {
            // evidence deve ser tolerante: não falhar snapshot por falha de métricas
        }

        // Prompt 43.2: assessments + documentos corretivos internos
        try {
            var assessmentStatusCounts = new HashMap<FiscalAdjustmentAssessmentStatus, Integer>();
            for (var row : assessmentRepository.countByTenantAndTurnoGroupByStatus(tenantId, turnoId)) {
                if (row == null || row.length < 2) continue;
                FiscalAdjustmentAssessmentStatus st = (FiscalAdjustmentAssessmentStatus) row[0];
                Long cnt = (Long) row[1];
                assessmentStatusCounts.put(st, cnt != null ? cnt.intValue() : 0);
            }
            out.setPendingFiscalAssessments(assessmentStatusCounts.getOrDefault(FiscalAdjustmentAssessmentStatus.PENDING, 0));
            out.setNoImpactAssessments(assessmentStatusCounts.getOrDefault(FiscalAdjustmentAssessmentStatus.NO_FISCAL_IMPACT, 0));
            out.setAssessmentsRequiringCreditNote(assessmentStatusCounts.getOrDefault(FiscalAdjustmentAssessmentStatus.REQUIRES_CREDIT_NOTE, 0));
            out.setAssessmentsRequiringDebitNote(assessmentStatusCounts.getOrDefault(FiscalAdjustmentAssessmentStatus.REQUIRES_DEBIT_NOTE, 0));
            out.setCorrectionIssuedAssessments(assessmentStatusCounts.getOrDefault(FiscalAdjustmentAssessmentStatus.CORRECTION_ISSUED, 0));

            if (out.getPendingFiscalAssessments() != null && out.getPendingFiscalAssessments() > 0) warnings.add("FISCAL_ASSESSMENT_PENDING");
            if (out.getAssessmentsRequiringCreditNote() != null && out.getAssessmentsRequiringCreditNote() > 0) warnings.add("FISCAL_ASSESSMENT_REQUIRES_CREDIT_NOTE");
            if (out.getAssessmentsRequiringDebitNote() != null && out.getAssessmentsRequiringDebitNote() > 0) warnings.add("FISCAL_ASSESSMENT_REQUIRES_DEBIT_NOTE");

            // items (determinísticos + hash)
            for (var a : assessmentRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId)) {
                if (a == null) continue;
                TaxEvidenceAssessmentItemDTO it = new TaxEvidenceAssessmentItemDTO();
                it.setAssessmentId(a.getId());
                it.setCaixaAdjustmentId(a.getAdjustment() != null ? a.getAdjustment().getId() : null);
                it.setOriginalFiscalDocumentId(a.getOriginalFiscalDocument() != null ? a.getOriginalFiscalDocument().getId() : null);
                it.setStatus(a.getStatus());
                it.setImpactType(a.getImpactType());
                it.setAssessedAt(a.getAssessedAt());
                it.setAssessmentHash(hashForAssessment(a));
                assessmentItems.add(it);
            }

            int creditCount = (int) corrections.stream().filter(d -> d.getDocumentType() == FiscalDocumentType.INTERNAL_CREDIT_NOTE).count();
            int debitCount = (int) corrections.stream().filter(d -> d.getDocumentType() == FiscalDocumentType.INTERNAL_DEBIT_NOTE).count();
            out.setTotalCorrectionDocuments(corrections.size());
            out.setCreditNotesCount(creditCount);
            out.setDebitNotesCount(debitCount);

            for (FiscalDocument d : corrections) {
                if (d == null) continue;
                correctionNet = correctionNet.add(nz(d.getTaxableAmount()));
                correctionTax = correctionTax.add(nz(d.getTaxAmount()));
                correctionGross = correctionGross.add(nz(d.getTotalAmount()));

                List<FiscalDocumentLine> lines = fiscalDocumentLineRepository.findByTenantIdAndFiscalDocumentId(tenantId, d.getId());
                String docHash = hashForDocument(d, lines);

                TaxEvidenceCorrectionDocumentItemDTO it = new TaxEvidenceCorrectionDocumentItemDTO();
                it.setCorrectionDocumentId(d.getId());
                it.setOriginalFiscalDocumentId(d.getOriginalFiscalDocument() != null ? d.getOriginalFiscalDocument().getId() : null);
                it.setAssessmentId(d.getFiscalAdjustmentAssessment() != null ? d.getFiscalAdjustmentAssessment().getId() : null);
                it.setCaixaAdjustmentId(d.getCaixaOperadorAdjustment() != null ? d.getCaixaOperadorAdjustment().getId() : null);
                it.setDocumentType(d.getDocumentType());
                it.setStatus(d.getStatus());
                it.setDocumentNumber(d.getDocumentNumber());
                it.setSeries(d.getSeries());
                it.setIssuedAt(d.getIssuedAt());
                it.setCorrectionSource(d.getCorrectionSource());
                it.setCorrectionReasonHash(hashText(d.getCorrectionReason()));
                it.setNetAmount(d.getTaxableAmount());
                it.setTaxAmount(d.getTaxAmount());
                it.setTotalAmount(d.getTotalAmount());
                it.setDocumentHash(docHash);
                applyOfficialFields(it, submissionByDocumentId.get(d.getId()));
                correctionItems.add(it);
            }
        } catch (Exception ignored) {
            // tolerante
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
            applyOfficialFields(it, submissionByDocumentId.get(d.getId()));
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
        out.setCorrectionNetAmount(correctionNet);
        out.setCorrectionTaxAmount(correctionTax);
        out.setCorrectionGrossAmount(correctionGross);

        // Prompt 45: métricas de submissão oficial (placeholder)
        TenantOfficialFiscalProfile officialProfile = officialProps.isEnabled() ? officialProfileRepository.findByTenantId(tenantId).orElse(null) : null;
        out.setOfficialFiscalEnabled(officialProfile != null && officialProfile.isOfficialEnabled());
        out.setOfficialEnvironment(officialProfile != null && officialProfile.getEnvironment() != null ? officialProfile.getEnvironment().name() : null);
        out.setTotalOfficialSubmissions(officialSubmissions.size());
        out.setPendingOfficialSubmissions((int) officialSubmissions.stream().filter(s -> s != null && s.getStatus() == OfficialFiscalSubmissionStatus.PENDING_RESULT).count());
        out.setSubmittedOfficialSubmissions((int) officialSubmissions.stream().filter(s -> s != null && s.getStatus() == OfficialFiscalSubmissionStatus.SUBMITTED).count());
        out.setAcceptedOfficialSubmissions((int) officialSubmissions.stream().filter(s -> s != null && s.getStatus() == OfficialFiscalSubmissionStatus.ACCEPTED).count());
        out.setRejectedOfficialSubmissions((int) officialSubmissions.stream().filter(s -> s != null && s.getStatus() == OfficialFiscalSubmissionStatus.REJECTED).count());
        out.setFailedOfficialSubmissions((int) officialSubmissions.stream().filter(s -> s != null && (s.getStatus() == OfficialFiscalSubmissionStatus.FAILED_PERMANENT || s.getStatus() == OfficialFiscalSubmissionStatus.FAILED_RETRYABLE)).count());
        int issuedDocs = (int) allDocs.stream().filter(d -> d != null && d.getStatus() == FiscalDocumentStatus.ISSUED).count();
        out.setDocumentsIssuedNotSubmittedOfficially(Math.max(0, issuedDocs - submissionByDocumentId.size()));
        if (out.getOfficialFiscalEnabled() != null && out.getOfficialFiscalEnabled() && out.getDocumentsIssuedNotSubmittedOfficially() != null && out.getDocumentsIssuedNotSubmittedOfficially() > 0) {
            warnings.add("FISCAL_DOCUMENT_NOT_SUBMITTED_OFFICIALLY");
        }

        out.setWarnings(warnings.stream().distinct().toList());
        out.setDocuments(items);
        out.setCorrectionDocuments(correctionItems);
        out.setAssessments(assessmentItems);

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

    private static void applyOfficialFields(TaxEvidenceDocumentItemDTO item, OfficialFiscalSubmission s) {
        if (item == null || s == null) return;
        item.setOfficialSubmissionId(s.getId());
        item.setOfficialSubmissionStatus(s.getStatus() != null ? s.getStatus().name() : null);
        item.setOfficialRequestId(s.getRequestId());
        item.setOfficialStatusCode(s.getOfficialStatusCode());
        item.setOfficialStatusMessage(s.getOfficialStatusMessage());
        item.setOfficialAcceptedAt(s.getAcceptedAt());
        item.setOfficialRejectedAt(s.getRejectedAt());
        item.setOfficialPayloadHash(s.getPayloadHash());
        item.setOfficialSignedPayloadHash(s.getSignedPayloadHash());
    }

    private static void applyOfficialFields(TaxEvidenceCorrectionDocumentItemDTO item, OfficialFiscalSubmission s) {
        if (item == null || s == null) return;
        item.setOfficialSubmissionId(s.getId());
        item.setOfficialSubmissionStatus(s.getStatus() != null ? s.getStatus().name() : null);
        item.setOfficialRequestId(s.getRequestId());
        item.setOfficialStatusCode(s.getOfficialStatusCode());
        item.setOfficialStatusMessage(s.getOfficialStatusMessage());
        item.setOfficialAcceptedAt(s.getAcceptedAt());
        item.setOfficialRejectedAt(s.getRejectedAt());
        item.setOfficialPayloadHash(s.getPayloadHash());
        item.setOfficialSignedPayloadHash(s.getSignedPayloadHash());
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

    private String hashForAssessment(com.restaurante.model.entity.FiscalAdjustmentAssessment a) {
        String canonical = assessmentCanonicalString(a);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private String assessmentCanonicalString(com.restaurante.model.entity.FiscalAdjustmentAssessment a) {
        return "assessmentId=" + v(a != null ? a.getId() : null)
                + "|tenantId=" + v(a != null && a.getTenant() != null ? a.getTenant().getId() : null)
                + "|adjustmentId=" + v(a != null && a.getAdjustment() != null ? a.getAdjustment().getId() : null)
                + "|originalDocId=" + v(a != null && a.getOriginalFiscalDocument() != null ? a.getOriginalFiscalDocument().getId() : null)
                + "|status=" + (a != null && a.getStatus() != null ? a.getStatus().name() : "null")
                + "|impactType=" + (a != null && a.getImpactType() != null ? a.getImpactType().name() : "null")
                + "|assessedAt=" + v(a != null ? a.getAssessedAt() : null)
                + "|decisionReasonHash=" + hashText(a != null ? a.getDecisionReason() : null);
    }

    private String hashText(String text) {
        String t = text != null ? text.replaceAll("[\\r\\n\\t]", " ").trim() : "";
        if (t.length() > 500) t = t.substring(0, 500);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(t.getBytes(StandardCharsets.UTF_8));
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
                .append("|originalDocId=").append(v(d != null && d.getOriginalFiscalDocument() != null ? d.getOriginalFiscalDocument().getId() : null))
                .append("|correctionSource=").append(d != null && d.getCorrectionSource() != null ? d.getCorrectionSource().name() : "null")
                .append("|caixaAdjId=").append(v(d != null && d.getCaixaOperadorAdjustment() != null ? d.getCaixaOperadorAdjustment().getId() : null))
                .append("|assessmentId=").append(v(d != null && d.getFiscalAdjustmentAssessment() != null ? d.getFiscalAdjustmentAssessment().getId() : null))
                .append("|correctionReasonHash=").append(hashText(d != null ? d.getCorrectionReason() : null))
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
