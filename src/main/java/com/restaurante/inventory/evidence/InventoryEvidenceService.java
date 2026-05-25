package com.restaurante.inventory.evidence;

import com.restaurante.financeiro.snapshot.evidence.dto.InventoryEvidenceConsumptionItemDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.InventoryEvidenceSectionDTO;
import com.restaurante.inventory.config.InventoryProperties;
import com.restaurante.inventory.repository.InventoryConsumptionRecordRepository;
import com.restaurante.inventory.repository.InventoryMovementRepository;
import com.restaurante.model.entity.InventoryConsumptionRecord;
import com.restaurante.model.entity.InventoryMovement;
import com.restaurante.model.enums.InventoryMovementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static com.restaurante.inventory.util.InventoryMath.scale;

@Service
@RequiredArgsConstructor
public class InventoryEvidenceService {

    private final InventoryMovementRepository movementRepository;
    private final InventoryConsumptionRecordRepository consumptionRecordRepository;
    private final InventoryProperties props;

    public InventoryEvidenceSectionDTO buildForTurno(Long tenantId, Long turnoId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        InventoryEvidenceSectionDTO out = new InventoryEvidenceSectionDTO();
        out.setGeneratedAt(LocalDateTime.now());
        out.setTenantId(tenantId);
        out.setTurnoId(turnoId);
        out.setPeriodStart(periodStart);
        out.setPeriodEnd(periodEnd);

        if (!props.isEnabled() || tenantId == null) {
            out.setWarnings(List.of("INVENTORY_EVIDENCE_DISABLED"));
            out.setConsumptionRecords(List.of());
            out.setTotalMovements(0);
            out.setStockInCount(0);
            out.setSaleConsumptionCount(0);
            out.setWasteCount(0);
            out.setAdjustmentCount(0);
            out.setTotalStockInCost(BigDecimal.ZERO);
            out.setTotalConsumptionCost(BigDecimal.ZERO);
            out.setTotalWasteCost(BigDecimal.ZERO);
            out.setTotalAdjustmentCost(BigDecimal.ZERO);
            out.setTotalRevenue(BigDecimal.ZERO);
            out.setTotalNetRevenue(BigDecimal.ZERO);
            out.setTotalTaxAmount(BigDecimal.ZERO);
            out.setTotalCogs(BigDecimal.ZERO);
            out.setEstimatedGrossMargin(BigDecimal.ZERO);
            out.setEstimatedGrossMarginPercentage(BigDecimal.ZERO);
            out.setWarningCount(0);
            return out;
        }

        List<InventoryMovement> movements = movementRepository.findAllForEvidence(tenantId, periodStart, periodEnd);
        List<InventoryConsumptionRecord> consumptions = consumptionRecordRepository.findAllForEvidence(tenantId, periodStart, periodEnd);

        int stockIn = 0;
        int sale = 0;
        int waste = 0;
        int adjustment = 0;
        BigDecimal stockInCost = BigDecimal.ZERO;
        BigDecimal saleCost = BigDecimal.ZERO;
        BigDecimal wasteCost = BigDecimal.ZERO;
        BigDecimal adjustmentCost = BigDecimal.ZERO;

        for (InventoryMovement m : movements) {
            if (m == null || m.getMovementType() == null) continue;
            if (m.getMovementType() == InventoryMovementType.PURCHASE_IN) {
                stockIn++;
                stockInCost = stockInCost.add(nz(m.getTotalCost()));
            } else if (m.getMovementType() == InventoryMovementType.SALE_CONSUMPTION) {
                sale++;
                saleCost = saleCost.add(nz(m.getTotalCost()));
            } else if (m.getMovementType() == InventoryMovementType.WASTE) {
                waste++;
                wasteCost = wasteCost.add(nz(m.getTotalCost()));
            } else if (m.getMovementType() == InventoryMovementType.ADJUSTMENT_IN || m.getMovementType() == InventoryMovementType.ADJUSTMENT_OUT) {
                adjustment++;
                adjustmentCost = adjustmentCost.add(nz(m.getTotalCost()));
            }
        }

        List<String> warnings = new ArrayList<>();
        List<InventoryEvidenceConsumptionItemDTO> items = new ArrayList<>();

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalNetRevenue = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        int warningCount = 0;

        for (InventoryConsumptionRecord r : consumptions) {
            if (r == null) continue;
            InventoryEvidenceConsumptionItemDTO it = new InventoryEvidenceConsumptionItemDTO();
            it.setConsumptionRecordId(r.getId());
            it.setPedidoId(r.getPedido() != null ? r.getPedido().getId() : null);
            it.setPagamentoId(r.getPagamento() != null ? r.getPagamento().getId() : null);
            it.setStatus(r.getStatus());
            it.setTriggerType(r.getTriggerType());
            it.setConsumedAt(r.getConsumedAt());
            it.setTotalCost(r.getTotalCost());
            it.setNetRevenueAmount(r.getNetRevenueAmount());
            it.setEstimatedMarginAmount(r.getEstimatedMarginAmount());
            it.setEstimatedMarginPercentage(r.getEstimatedMarginPercentage());
            it.setWarningCount(r.getWarningCount());
            it.setConsumptionHash(hashForConsumption(r));
            items.add(it);

            totalRevenue = totalRevenue.add(nz(r.getGrossRevenueAmount()));
            totalNetRevenue = totalNetRevenue.add(nz(r.getNetRevenueAmount()));
            totalTax = totalTax.add(nz(r.getTaxAmount()));
            totalCogs = totalCogs.add(nz(r.getTotalCost()));
            warningCount += r.getWarningCount() != null ? r.getWarningCount() : 0;
        }

        BigDecimal margin = totalNetRevenue.subtract(totalCogs);
        BigDecimal marginPct = BigDecimal.ZERO;
        if (totalNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            marginPct = margin.divide(totalNetRevenue, props.getMath().getCalculationScale(), props.getMath().getRoundingMode())
                    .multiply(new BigDecimal("100"));
        }

        out.setTotalMovements(movements.size());
        out.setStockInCount(stockIn);
        out.setSaleConsumptionCount(sale);
        out.setWasteCount(waste);
        out.setAdjustmentCount(adjustment);

        out.setTotalStockInCost(scale(stockInCost, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        out.setTotalConsumptionCost(scale(saleCost, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        out.setTotalWasteCost(scale(wasteCost, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        out.setTotalAdjustmentCost(scale(adjustmentCost, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));

        out.setTotalRevenue(scale(totalRevenue, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        out.setTotalNetRevenue(scale(totalNetRevenue, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        out.setTotalTaxAmount(scale(totalTax, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        out.setTotalCogs(scale(totalCogs, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));

        out.setEstimatedGrossMargin(scale(margin, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        out.setEstimatedGrossMarginPercentage(scale(marginPct, 6, props.getMath().getRoundingMode()));

        out.setWarningCount(warningCount);
        out.setWarnings(warnings);
        out.setConsumptionRecords(items);
        return out;
    }

    private String hashForConsumption(InventoryConsumptionRecord r) {
        String canonical = canonicalString(r);
        return sha256Hex(canonical);
    }

    private String canonicalString(InventoryConsumptionRecord r) {
        BigDecimal totalCost = scale(nz(r.getTotalCost()), props.getMath().getMonetaryScale(), props.getMath().getRoundingMode());
        BigDecimal net = scale(nz(r.getNetRevenueAmount()), props.getMath().getMonetaryScale(), props.getMath().getRoundingMode());
        BigDecimal margin = scale(nz(r.getEstimatedMarginAmount()), props.getMath().getMonetaryScale(), props.getMath().getRoundingMode());
        return String.join("|",
                "consumptionId=" + r.getId(),
                "tenantId=" + (r.getTenant() != null ? r.getTenant().getId() : null),
                "pedidoId=" + (r.getPedido() != null ? r.getPedido().getId() : null),
                "status=" + (r.getStatus() != null ? r.getStatus().name() : null),
                "trigger=" + (r.getTriggerType() != null ? r.getTriggerType().name() : null),
                "totalCost=" + totalCost,
                "netRevenue=" + net,
                "margin=" + margin,
                "warningCount=" + (r.getWarningCount() != null ? r.getWarningCount() : 0)
        );
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

