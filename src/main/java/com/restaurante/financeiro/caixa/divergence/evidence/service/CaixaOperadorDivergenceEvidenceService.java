package com.restaurante.financeiro.caixa.divergence.evidence.service;

import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorAdjustmentRepository;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashAdjustmentEvidenceItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashDivergenceEvidenceItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashDivergenceEvidenceSectionDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashEvidenceSectionDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashSessionEvidenceItemDTO;
import com.restaurante.model.entity.CaixaOperadorAdjustment;
import com.restaurante.model.entity.CaixaOperadorDivergence;
import com.restaurante.model.enums.CaixaOperadorAdjustmentStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaixaOperadorDivergenceEvidenceService {

    private final CaixaOperadorDivergenceRepository divergenceRepository;
    private final CaixaOperadorAdjustmentRepository adjustmentRepository;

    public OperatorCashDivergenceEvidenceSectionDTO buildForTurno(Long tenantId, Long turnoId) {
        if (tenantId == null || turnoId == null) return empty(tenantId, turnoId);

        List<CaixaOperadorDivergence> divergences = divergenceRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId);
        List<CaixaOperadorAdjustment> adjustments = adjustmentRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId);

        OperatorCashDivergenceEvidenceSectionDTO out = new OperatorCashDivergenceEvidenceSectionDTO();
        out.setGeneratedAt(LocalDateTime.now());
        out.setTenantId(tenantId);
        out.setTurnoId(turnoId);

        out.setTotalDivergences(divergences.size());
        out.setDraftDivergences((int) divergences.stream().filter(d -> d.getStatus() == CaixaOperadorDivergenceStatus.DRAFT).count());
        out.setSubmittedDivergences((int) divergences.stream().filter(d -> d.getStatus() == CaixaOperadorDivergenceStatus.SUBMITTED).count());
        out.setApprovedDivergences((int) divergences.stream().filter(d -> d.getStatus() == CaixaOperadorDivergenceStatus.APPROVED).count());
        out.setRejectedDivergences((int) divergences.stream().filter(d -> d.getStatus() == CaixaOperadorDivergenceStatus.REJECTED).count());
        out.setUnresolvedDivergences((int) divergences.stream().filter(d -> d.getStatus() == CaixaOperadorDivergenceStatus.DRAFT || d.getStatus() == CaixaOperadorDivergenceStatus.SUBMITTED).count());

        out.setTotalAdjustments(adjustments.size());
        out.setApprovedAdjustments((int) adjustments.stream().filter(a -> a.getStatus() == CaixaOperadorAdjustmentStatus.APPROVED).count());
        out.setTotalApprovedAdjustmentAmount(adjustments.stream()
                .filter(a -> a.getStatus() == CaixaOperadorAdjustmentStatus.APPROVED)
                .map(a -> nz(a.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        out.setDivergenceItems(divergences.stream().map(this::map).toList());
        out.setAdjustmentItems(adjustments.stream().map(this::map).toList());

        return out;
    }

    public void enrichOperatorCashEvidence(OperatorCashEvidenceSectionDTO cashEvidence,
                                          OperatorCashDivergenceEvidenceSectionDTO divergenceEvidence) {
        if (cashEvidence == null || cashEvidence.getSessions() == null || cashEvidence.getSessions().isEmpty()) return;
        if (divergenceEvidence == null) return;

        Map<Long, List<OperatorCashDivergenceEvidenceItemDTO>> byCaixaDiv = new HashMap<>();
        if (divergenceEvidence.getDivergenceItems() != null) {
            for (var d : divergenceEvidence.getDivergenceItems()) {
                if (d.getCaixaId() == null) continue;
                byCaixaDiv.computeIfAbsent(d.getCaixaId(), k -> new java.util.ArrayList<>()).add(d);
            }
        }
        Map<Long, List<OperatorCashAdjustmentEvidenceItemDTO>> byCaixaAdj = new HashMap<>();
        if (divergenceEvidence.getAdjustmentItems() != null) {
            for (var a : divergenceEvidence.getAdjustmentItems()) {
                if (a.getCaixaId() == null) continue;
                byCaixaAdj.computeIfAbsent(a.getCaixaId(), k -> new java.util.ArrayList<>()).add(a);
            }
        }

        for (OperatorCashSessionEvidenceItemDTO s : cashEvidence.getSessions()) {
            if (s == null || s.getCaixaId() == null) continue;
            List<OperatorCashDivergenceEvidenceItemDTO> d = byCaixaDiv.getOrDefault(s.getCaixaId(), List.of());
            List<OperatorCashAdjustmentEvidenceItemDTO> a = byCaixaAdj.getOrDefault(s.getCaixaId(), List.of());
            int unresolved = (int) d.stream().filter(it -> it.getStatus() == CaixaOperadorDivergenceStatus.DRAFT || it.getStatus() == CaixaOperadorDivergenceStatus.SUBMITTED).count();
            int approvedAdj = (int) a.stream().filter(it -> it.getStatus() == CaixaOperadorAdjustmentStatus.APPROVED).count();
            BigDecimal adjTotal = a.stream().filter(it -> it.getStatus() == CaixaOperadorAdjustmentStatus.APPROVED).map(it -> nz(it.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

            s.setDivergencesCount(d.size());
            s.setUnresolvedDivergencesCount(unresolved);
            s.setApprovedAdjustmentsCount(approvedAdj);
            s.setAdjustmentsTotalAmount(adjTotal);
            s.setHasUnresolvedDivergence(unresolved > 0);
            s.setHasApprovedAdjustment(approvedAdj > 0);
        }
    }

    private OperatorCashDivergenceEvidenceSectionDTO empty(Long tenantId, Long turnoId) {
        OperatorCashDivergenceEvidenceSectionDTO out = new OperatorCashDivergenceEvidenceSectionDTO();
        out.setGeneratedAt(LocalDateTime.now());
        out.setTenantId(tenantId);
        out.setTurnoId(turnoId);
        out.setTotalDivergences(0);
        out.setDraftDivergences(0);
        out.setSubmittedDivergences(0);
        out.setApprovedDivergences(0);
        out.setRejectedDivergences(0);
        out.setUnresolvedDivergences(0);
        out.setTotalAdjustments(0);
        out.setApprovedAdjustments(0);
        out.setTotalApprovedAdjustmentAmount(BigDecimal.ZERO);
        out.setDivergenceItems(List.of());
        out.setAdjustmentItems(List.of());
        return out;
    }

    private OperatorCashDivergenceEvidenceItemDTO map(CaixaOperadorDivergence d) {
        OperatorCashDivergenceEvidenceItemDTO out = new OperatorCashDivergenceEvidenceItemDTO();
        out.setDivergenceId(d.getId());
        out.setCaixaId(d.getCaixaOperadorSession() != null ? d.getCaixaOperadorSession().getId() : null);
        out.setStatus(d.getStatus());
        out.setType(d.getType());
        out.setSeverity(d.getSeverity());
        out.setPaymentMethod(d.getPaymentMethod());
        out.setDifferenceAmount(d.getDifferenceAmount());
        out.setAbsoluteDifferenceAmount(d.getAbsoluteDifferenceAmount());
        out.setReasonCategory(d.getReasonCategory());
        out.setSubmittedAt(d.getSubmittedAt());
        out.setReviewedAt(d.getReviewedAt());
        out.setDivergenceHash(hash("div", canonical(d)));
        return out;
    }

    private OperatorCashAdjustmentEvidenceItemDTO map(CaixaOperadorAdjustment a) {
        OperatorCashAdjustmentEvidenceItemDTO out = new OperatorCashAdjustmentEvidenceItemDTO();
        out.setAdjustmentId(a.getId());
        out.setDivergenceId(a.getDivergence() != null ? a.getDivergence().getId() : null);
        out.setCaixaId(a.getCaixaOperadorSession() != null ? a.getCaixaOperadorSession().getId() : null);
        out.setStatus(a.getStatus());
        out.setAdjustmentType(a.getAdjustmentType());
        out.setPaymentMethod(a.getPaymentMethod());
        out.setAmount(a.getAmount());
        out.setDirection(a.getDirection());
        out.setApprovedAt(a.getApprovedAt());
        out.setAdjustmentHash(hash("adj", canonical(a)));
        return out;
    }

    private static String canonical(CaixaOperadorDivergence d) {
        return "divergenceId=" + v(d != null ? d.getId() : null)
                + "|tenantId=" + v(d != null && d.getTenant() != null ? d.getTenant().getId() : null)
                + "|caixaId=" + v(d != null && d.getCaixaOperadorSession() != null ? d.getCaixaOperadorSession().getId() : null)
                + "|status=" + (d != null && d.getStatus() != null ? d.getStatus().name() : "null")
                + "|type=" + (d != null && d.getType() != null ? d.getType().name() : "null")
                + "|severity=" + (d != null && d.getSeverity() != null ? d.getSeverity().name() : "null")
                + "|paymentMethod=" + (d != null && d.getPaymentMethod() != null ? d.getPaymentMethod().name() : "null")
                + "|expected=" + money(d != null ? d.getExpectedAmount() : null)
                + "|declared=" + money(d != null ? d.getDeclaredAmount() : null)
                + "|diff=" + money(d != null ? d.getDifferenceAmount() : null)
                + "|absDiff=" + money(d != null ? d.getAbsoluteDifferenceAmount() : null)
                + "|reasonCategory=" + (d != null && d.getReasonCategory() != null ? d.getReasonCategory().name() : "null")
                + "|submittedAt=" + v(d != null ? d.getSubmittedAt() : null)
                + "|reviewedAt=" + v(d != null ? d.getReviewedAt() : null)
                + "|reviewNotesHash=" + notesHash(d != null ? d.getReviewNotes() : null);
    }

    private static String canonical(CaixaOperadorAdjustment a) {
        return "adjustmentId=" + v(a != null ? a.getId() : null)
                + "|tenantId=" + v(a != null && a.getTenant() != null ? a.getTenant().getId() : null)
                + "|divergenceId=" + v(a != null && a.getDivergence() != null ? a.getDivergence().getId() : null)
                + "|caixaId=" + v(a != null && a.getCaixaOperadorSession() != null ? a.getCaixaOperadorSession().getId() : null)
                + "|status=" + (a != null && a.getStatus() != null ? a.getStatus().name() : "null")
                + "|type=" + (a != null && a.getAdjustmentType() != null ? a.getAdjustmentType().name() : "null")
                + "|paymentMethod=" + (a != null && a.getPaymentMethod() != null ? a.getPaymentMethod().name() : "null")
                + "|amount=" + money(a != null ? a.getAmount() : null)
                + "|direction=" + (a != null && a.getDirection() != null ? a.getDirection().name() : "null")
                + "|approvedAt=" + v(a != null ? a.getApprovedAt() : null);
    }

    private static String hash(String prefix, String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((prefix + "|" + canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String notesHash(String notes) {
        if (notes == null) return "null";
        String t = notes.trim();
        if (t.isEmpty()) return "null";
        if (t.length() > 500) {
            t = t.substring(0, 500);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(t.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "error";
        }
    }

    private static String v(Object o) {
        return o == null ? "null" : String.valueOf(o);
    }

    private static String money(BigDecimal v) {
        if (v == null) return "null";
        try {
            return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return v.toPlainString();
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
