package com.restaurante.inventory.service;

import com.restaurante.inventory.config.InventoryProperties;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.InventoryReturnLine;
import com.restaurante.model.entity.InventoryReturnRecord;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.enums.FiscalDocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.restaurante.inventory.util.InventoryMath.scale;

@Service
@RequiredArgsConstructor
public class InventoryReturnFinancialCalculator {

    private final InventoryProperties props;

    public FinancialTotals calculate(InventoryReturnRecord record, List<InventoryReturnLine> lines) {
        if (record == null) return FinancialTotals.zero();

        FiscalDocument creditNote = record.getFiscalCreditNote();
        if (creditNote == null) creditNote = record.getFiscalCorrectionDocument();

        if (creditNote != null && creditNote.getDocumentType() == FiscalDocumentType.INTERNAL_CREDIT_NOTE) {
            BigDecimal revenue = nz(creditNote.getTaxableAmount()).add(nz(creditNote.getExemptAmount()));
            BigDecimal tax = nz(creditNote.getTaxAmount());
            return new FinancialTotals(
                    scale(revenue, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()),
                    scale(tax, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()),
                    false
            );
        }

        FiscalDocument original = record.getFiscalDocument();
        if (original != null) {
            BigDecimal netRevenue = nz(original.getTaxableAmount()).add(nz(original.getExemptAmount()));
            BigDecimal tax = nz(original.getTaxAmount());

            BigDecimal grossReturned = estimateGrossReturned(lines);
            BigDecimal grossPedido = record.getPedido() != null ? nz(record.getPedido().getTotal()) : BigDecimal.ZERO;
            BigDecimal ratio = BigDecimal.ZERO;
            if (grossPedido.compareTo(BigDecimal.ZERO) > 0 && grossReturned.compareTo(BigDecimal.ZERO) > 0) {
                ratio = grossReturned.divide(grossPedido, props.getMath().getCalculationScale(), props.getMath().getRoundingMode());
            }

            BigDecimal rev = netRevenue.multiply(ratio);
            BigDecimal taxRev = tax.multiply(ratio);
            return new FinancialTotals(
                    scale(rev, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()),
                    scale(taxRev, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()),
                    true
            );
        }

        BigDecimal grossReturned = estimateGrossReturned(lines);
        return new FinancialTotals(
                scale(grossReturned, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()),
                BigDecimal.ZERO,
                true
        );
    }

    public void applyToLines(InventoryReturnRecord record, List<InventoryReturnLine> lines, BigDecimal totalRevenueReversed, BigDecimal totalTaxReversed) {
        if (lines == null || lines.isEmpty()) return;
        BigDecimal totalLineCost = BigDecimal.ZERO;
        BigDecimal totalLineGross = BigDecimal.ZERO;
        for (InventoryReturnLine l : lines) {
            totalLineCost = totalLineCost.add(nz(l.getTotalCostReversed()));
            totalLineGross = totalLineGross.add(estimateGrossReturnedForLine(l));
        }

        for (InventoryReturnLine l : lines) {
            BigDecimal gross = estimateGrossReturnedForLine(l);
            BigDecimal ratio = BigDecimal.ZERO;
            if (totalLineGross.compareTo(BigDecimal.ZERO) > 0) {
                ratio = gross.divide(totalLineGross, props.getMath().getCalculationScale(), props.getMath().getRoundingMode());
            }
            BigDecimal lineRevenue = nz(totalRevenueReversed).multiply(ratio);
            BigDecimal lineTax = nz(totalTaxReversed).multiply(ratio);
            BigDecimal lineMargin = lineRevenue.subtract(nz(l.getTotalCostReversed()));

            l.setTotalRevenueReversed(scale(lineRevenue, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
            l.setTotalTaxReversed(scale(lineTax, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
            l.setTotalMarginReversed(scale(lineMargin, props.getMath().getMonetaryScale(), props.getMath().getRoundingMode()));
        }
    }

    private BigDecimal estimateGrossReturned(List<InventoryReturnLine> lines) {
        BigDecimal out = BigDecimal.ZERO;
        if (lines == null) return out;
        for (InventoryReturnLine l : lines) {
            out = out.add(estimateGrossReturnedForLine(l));
        }
        return out;
    }

    private BigDecimal estimateGrossReturnedForLine(InventoryReturnLine l) {
        if (l == null || l.getPedidoItem() == null) return BigDecimal.ZERO;
        ItemPedido it = l.getPedidoItem();
        BigDecimal unitPrice = it.getPrecoUnitario() != null ? it.getPrecoUnitario() : BigDecimal.ZERO;
        BigDecimal qty = l.getQuantityReturned() != null ? l.getQuantityReturned() : BigDecimal.ZERO;
        return unitPrice.multiply(qty);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public record FinancialTotals(BigDecimal revenueReversed, BigDecimal taxReversed, boolean estimated) {
        static FinancialTotals zero() {
            return new FinancialTotals(BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
    }
}

