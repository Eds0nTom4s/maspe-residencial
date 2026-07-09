package com.restaurante.financeiro.caixa.evidence.service;

import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionItemRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashByDeviceSummaryDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashByOperatorSummaryDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashEvidenceSectionDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashSessionEvidenceItemDTO;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CaixaOperadorEvidenceService {

    private static final String SOURCE_VERSION = "operator-cash-v1";

    private final CaixaOperadorSessionRepository caixaRepository;
    private final CaixaOperadorSessionItemRepository itemRepository;

    @Transactional(readOnly = true)
    public OperatorCashEvidenceSectionDTO buildForTurno(Long tenantId,
                                                        Long instituicaoId,
                                                        Long unidadeId,
                                                        Long turnoId,
                                                        LocalDateTime periodStart,
                                                        LocalDateTime periodEnd) {
        OperatorCashEvidenceSectionDTO out = new OperatorCashEvidenceSectionDTO();
        out.setSourceVersion(SOURCE_VERSION);
        out.setGeneratedAt(LocalDateTime.now());
        out.setTenantId(tenantId);
        out.setInstituicaoId(instituicaoId);
        out.setUnidadeId(unidadeId);
        out.setTurnoId(turnoId);
        out.setPeriodStart(periodStart);
        out.setPeriodEnd(periodEnd);

        if (tenantId == null || turnoId == null) {
            out.setSessions(List.of());
            out.setByOperator(List.of());
            out.setByDevice(List.of());
            out.setWarnings(List.of("CAIXA_OPERADOR_EVIDENCE_TURNO_OR_TENANT_MISSING"));
            out.setTotalCashSessions(0);
            out.setOpenCashSessions(0);
            out.setClosedCashSessions(0);
            out.setReviewedCashSessions(0);
            out.setDisputedCashSessions(0);
            out.setCancelledCashSessions(0);
            out.setExpectedCashAmount(BigDecimal.ZERO);
            out.setDeclaredCashAmount(BigDecimal.ZERO);
            out.setCashDifferenceAmount(BigDecimal.ZERO);
            out.setExpectedTpaAmount(BigDecimal.ZERO);
            out.setDeclaredTpaAmount(BigDecimal.ZERO);
            out.setTpaDifferenceAmount(BigDecimal.ZERO);
            out.setExpectedManualTotalAmount(BigDecimal.ZERO);
            out.setDeclaredManualTotalAmount(BigDecimal.ZERO);
            out.setManualDifferenceAmount(BigDecimal.ZERO);
            out.setExpectedAppyPayAmount(BigDecimal.ZERO);
            return out;
        }

        List<CaixaOperadorSession> caixas = caixaRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId);
        List<String> warnings = new ArrayList<>();

        BigDecimal expectedCash = BigDecimal.ZERO;
        BigDecimal declaredCash = BigDecimal.ZERO;
        BigDecimal expectedTpa = BigDecimal.ZERO;
        BigDecimal declaredTpa = BigDecimal.ZERO;
        BigDecimal expectedManual = BigDecimal.ZERO;
        BigDecimal declaredManual = BigDecimal.ZERO;
        BigDecimal expectedAppyPay = BigDecimal.ZERO;

        int open = 0;
        int closed = 0;
        int reviewed = 0;
        int disputed = 0;
        int cancelled = 0;

        List<OperatorCashSessionEvidenceItemDTO> sessionItems = new ArrayList<>();

        Map<Long, Agg> byOperator = new HashMap<>();
        Map<Long, Agg> byDevice = new HashMap<>();

        for (CaixaOperadorSession c : caixas) {
            if (unidadeId != null && c.getUnidadeAtendimento() != null && !unidadeId.equals(c.getUnidadeAtendimento().getId())) {
                continue;
            }

            OperatorCashSessionEvidenceItemDTO it = new OperatorCashSessionEvidenceItemDTO();
            it.setCaixaId(c.getId());
            it.setStatus(c.getStatus());
            it.setUnidadeId(c.getUnidadeAtendimento() != null ? c.getUnidadeAtendimento().getId() : null);
            it.setTurnoId(c.getTurnoOperacional() != null ? c.getTurnoOperacional().getId() : null);
            it.setOperationalDeviceId(c.getDispositivoOperacional() != null ? c.getDispositivoOperacional().getId() : null);
            it.setOperadorUserId(c.getOperador() != null ? c.getOperador().getId() : null);
            it.setOpenedAt(c.getOpenedAt());
            it.setClosedAt(c.getClosedAt());
            it.setReviewedAt(c.getReviewedAt());
            it.setExpectedCashAmount(nz(c.getExpectedCashAmount()));
            it.setDeclaredCashAmount(c.getDeclaredCashAmount());
            it.setCashDifferenceAmount(c.getCashDifferenceAmount());
            it.setExpectedTpaAmount(nz(c.getExpectedTpaAmount()));
            it.setDeclaredTpaAmount(c.getDeclaredTpaAmount());
            it.setTpaDifferenceAmount(c.getTpaDifferenceAmount());
            it.setExpectedManualTotalAmount(nz(c.getExpectedManualTotalAmount()));
            it.setDeclaredManualTotalAmount(c.getDeclaredManualTotalAmount());
            it.setManualDifferenceAmount(c.getManualDifferenceAmount());
            it.setExpectedAppyPayAmount(nz(c.getExpectedAppyPayAmount()));

            long itemsCount = itemRepository.countByTenantIdAndCaixaOperadorSessionId(tenantId, c.getId());
            it.setItemsCount((int) itemsCount);

            boolean hasDiff = hasDifference(c);
            it.setHasDifference(hasDiff);
            it.setDifferenceSeverity(severityFor(c));

            it.setSessionHash(hashForSession(c, (int) itemsCount));
            sessionItems.add(it);

            if (c.getStatus() == CaixaOperadorSessionStatus.OPEN) {
                open++;
                warnings.add("OPEN_CASH_SESSION_INCLUDED_IN_TURNO");
            }
            if (c.getStatus() == CaixaOperadorSessionStatus.CLOSED) {
                closed++;
                warnings.add("CASH_SESSION_NOT_REVIEWED");
            }
            if (c.getStatus() == CaixaOperadorSessionStatus.REVIEWED) reviewed++;
            if (c.getStatus() == CaixaOperadorSessionStatus.DISPUTED) {
                disputed++;
                warnings.add("CASH_SESSION_DISPUTED");
            }
            if (c.getStatus() == CaixaOperadorSessionStatus.CANCELLED) cancelled++;

            if (itemsCount == 0 && c.getStatus() != CaixaOperadorSessionStatus.OPEN) {
                warnings.add("CASH_SESSION_WITHOUT_ITEMS");
            }
            if (hasDiff) warnings.add("CASH_SESSION_HAS_DIFFERENCE");

            expectedCash = expectedCash.add(nz(c.getExpectedCashAmount()));
            expectedTpa = expectedTpa.add(nz(c.getExpectedTpaAmount()));
            expectedManual = expectedManual.add(nz(c.getExpectedManualTotalAmount()));
            expectedAppyPay = expectedAppyPay.add(nz(c.getExpectedAppyPayAmount()));

            declaredCash = declaredCash.add(nz(c.getDeclaredCashAmount()));
            declaredTpa = declaredTpa.add(nz(c.getDeclaredTpaAmount()));
            declaredManual = declaredManual.add(nz(c.getDeclaredManualTotalAmount()));

            addAgg(byOperator, it.getOperadorUserId(), c);
            addAgg(byDevice, it.getOperationalDeviceId(), c);
        }

        sessionItems.sort(Comparator.comparing(OperatorCashSessionEvidenceItemDTO::getOpenedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OperatorCashSessionEvidenceItemDTO::getCaixaId, Comparator.nullsLast(Comparator.naturalOrder())));

        out.setSessions(sessionItems);
        out.setTotalCashSessions(sessionItems.size());
        out.setOpenCashSessions(open);
        out.setClosedCashSessions(closed);
        out.setReviewedCashSessions(reviewed);
        out.setDisputedCashSessions(disputed);
        out.setCancelledCashSessions(cancelled);

        out.setExpectedCashAmount(expectedCash);
        out.setDeclaredCashAmount(declaredCash);
        out.setCashDifferenceAmount(declaredCash.subtract(expectedCash));

        out.setExpectedTpaAmount(expectedTpa);
        out.setDeclaredTpaAmount(declaredTpa);
        out.setTpaDifferenceAmount(declaredTpa.subtract(expectedTpa));

        out.setExpectedManualTotalAmount(expectedManual);
        out.setDeclaredManualTotalAmount(declaredManual);
        out.setManualDifferenceAmount(declaredManual.subtract(expectedManual));

        out.setExpectedAppyPayAmount(expectedAppyPay);

        out.setByOperator(toOperatorSummaries(byOperator));
        out.setByDevice(toDeviceSummaries(byDevice));
        out.setWarnings(warnings.stream().distinct().toList());

        return out;
    }

    private static boolean hasDifference(CaixaOperadorSession c) {
        return nz(c.getManualDifferenceAmount()).compareTo(BigDecimal.ZERO) != 0
                || nz(c.getCashDifferenceAmount()).compareTo(BigDecimal.ZERO) != 0
                || nz(c.getTpaDifferenceAmount()).compareTo(BigDecimal.ZERO) != 0;
    }

    private static String severityFor(CaixaOperadorSession c) {
        if (c == null) return null;
        if (c.getStatus() == CaixaOperadorSessionStatus.DISPUTED) return "CRITICAL";
        if (c.getStatus() == CaixaOperadorSessionStatus.OPEN) return "WARNING";
        if (hasDifference(c)) return "WARNING";
        return "OK";
    }

    private String hashForSession(CaixaOperadorSession c, int itemsCount) {
        String canonical = canonicalString(c, itemsCount);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String canonicalString(CaixaOperadorSession c, int itemsCount) {
        return "caixaId=" + v(c != null ? c.getId() : null)
                + "|tenantId=" + v(c != null && c.getTenant() != null ? c.getTenant().getId() : null)
                + "|unidadeId=" + v(c != null && c.getUnidadeAtendimento() != null ? c.getUnidadeAtendimento().getId() : null)
                + "|turnoId=" + v(c != null && c.getTurnoOperacional() != null ? c.getTurnoOperacional().getId() : null)
                + "|deviceId=" + v(c != null && c.getDispositivoOperacional() != null ? c.getDispositivoOperacional().getId() : null)
                + "|operadorUserId=" + v(c != null && c.getOperador() != null ? c.getOperador().getId() : null)
                + "|status=" + (c != null && c.getStatus() != null ? c.getStatus().name() : "null")
                + "|openedAt=" + v(c != null ? c.getOpenedAt() : null)
                + "|closedAt=" + v(c != null ? c.getClosedAt() : null)
                + "|reviewedAt=" + v(c != null ? c.getReviewedAt() : null)
                + "|expectedCash=" + money(c != null ? c.getExpectedCashAmount() : null)
                + "|declaredCash=" + money(c != null ? c.getDeclaredCashAmount() : null)
                + "|cashDiff=" + money(c != null ? c.getCashDifferenceAmount() : null)
                + "|expectedTpa=" + money(c != null ? c.getExpectedTpaAmount() : null)
                + "|declaredTpa=" + money(c != null ? c.getDeclaredTpaAmount() : null)
                + "|tpaDiff=" + money(c != null ? c.getTpaDifferenceAmount() : null)
                + "|expectedManual=" + money(c != null ? c.getExpectedManualTotalAmount() : null)
                + "|declaredManual=" + money(c != null ? c.getDeclaredManualTotalAmount() : null)
                + "|manualDiff=" + money(c != null ? c.getManualDifferenceAmount() : null)
                + "|expectedAppyPay=" + money(c != null ? c.getExpectedAppyPayAmount() : null)
                + "|itemsCount=" + itemsCount;
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

    private static void addAgg(Map<Long, Agg> map, Long key, CaixaOperadorSession c) {
        if (key == null) return;
        Agg a = map.computeIfAbsent(key, k -> new Agg());
        a.totalSessions++;
        if (c.getStatus() == CaixaOperadorSessionStatus.CLOSED) a.closedSessions++;
        if (c.getStatus() == CaixaOperadorSessionStatus.REVIEWED) a.reviewedSessions++;
        if (c.getStatus() == CaixaOperadorSessionStatus.DISPUTED) a.disputedSessions++;
        a.expectedCash = a.expectedCash.add(nz(c.getExpectedCashAmount()));
        a.declaredCash = a.declaredCash.add(nz(c.getDeclaredCashAmount()));
        a.cashDiff = a.cashDiff.add(nz(c.getCashDifferenceAmount()));
        a.expectedTpa = a.expectedTpa.add(nz(c.getExpectedTpaAmount()));
        a.declaredTpa = a.declaredTpa.add(nz(c.getDeclaredTpaAmount()));
        a.tpaDiff = a.tpaDiff.add(nz(c.getTpaDifferenceAmount()));
        a.manualDiff = a.manualDiff.add(nz(c.getManualDifferenceAmount()));
    }

    private static List<OperatorCashByOperatorSummaryDTO> toOperatorSummaries(Map<Long, Agg> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    OperatorCashByOperatorSummaryDTO dto = new OperatorCashByOperatorSummaryDTO();
                    dto.setOperadorUserId(e.getKey());
                    Agg a = e.getValue();
                    dto.setTotalSessions(a.totalSessions);
                    dto.setClosedSessions(a.closedSessions);
                    dto.setReviewedSessions(a.reviewedSessions);
                    dto.setDisputedSessions(a.disputedSessions);
                    dto.setExpectedCashAmount(a.expectedCash);
                    dto.setDeclaredCashAmount(a.declaredCash);
                    dto.setCashDifferenceAmount(a.cashDiff);
                    dto.setExpectedTpaAmount(a.expectedTpa);
                    dto.setDeclaredTpaAmount(a.declaredTpa);
                    dto.setTpaDifferenceAmount(a.tpaDiff);
                    dto.setManualDifferenceAmount(a.manualDiff);
                    return dto;
                })
                .toList();
    }

    private static List<OperatorCashByDeviceSummaryDTO> toDeviceSummaries(Map<Long, Agg> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    OperatorCashByDeviceSummaryDTO dto = new OperatorCashByDeviceSummaryDTO();
                    dto.setOperationalDeviceId(e.getKey());
                    Agg a = e.getValue();
                    dto.setTotalSessions(a.totalSessions);
                    dto.setClosedSessions(a.closedSessions);
                    dto.setReviewedSessions(a.reviewedSessions);
                    dto.setDisputedSessions(a.disputedSessions);
                    dto.setExpectedCashAmount(a.expectedCash);
                    dto.setDeclaredCashAmount(a.declaredCash);
                    dto.setCashDifferenceAmount(a.cashDiff);
                    dto.setExpectedTpaAmount(a.expectedTpa);
                    dto.setDeclaredTpaAmount(a.declaredTpa);
                    dto.setTpaDifferenceAmount(a.tpaDiff);
                    dto.setManualDifferenceAmount(a.manualDiff);
                    return dto;
                })
                .toList();
    }

    private static final class Agg {
        int totalSessions = 0;
        int closedSessions = 0;
        int reviewedSessions = 0;
        int disputedSessions = 0;
        BigDecimal expectedCash = BigDecimal.ZERO;
        BigDecimal declaredCash = BigDecimal.ZERO;
        BigDecimal cashDiff = BigDecimal.ZERO;
        BigDecimal expectedTpa = BigDecimal.ZERO;
        BigDecimal declaredTpa = BigDecimal.ZERO;
        BigDecimal tpaDiff = BigDecimal.ZERO;
        BigDecimal manualDiff = BigDecimal.ZERO;
    }
}

