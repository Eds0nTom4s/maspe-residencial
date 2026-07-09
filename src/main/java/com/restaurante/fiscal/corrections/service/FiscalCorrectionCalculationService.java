package com.restaurante.fiscal.corrections.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FiscalCorrectionCalculationService {

    private final TaxProperties props;

    public CorrectionAmounts calculateSingleLineGrossAmount(FiscalDocument original, BigDecimal correctionGrossAmount) {
        if (original == null) throw new BusinessException("Documento original é obrigatório.");
        if (correctionGrossAmount == null) throw new BusinessException("amount é obrigatório.");
        if (correctionGrossAmount.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("amount deve ser > 0.");

        List<FiscalDocumentLine> lines = original.getLines();
        BigDecimal rateValue = resolveSingleVatRateValueOrThrow(lines);

        BigDecimal gross = money(correctionGrossAmount);
        if (rateValue == null || rateValue.compareTo(BigDecimal.ZERO) == 0) {
            return new CorrectionAmounts(gross, BigDecimal.ZERO, gross, rateValue);
        }

        BigDecimal divisor = BigDecimal.ONE.add(rateValue.divide(new BigDecimal("100"), props.getCalculationScale(), props.getRoundingMode()));
        BigDecimal net = gross.divide(divisor, props.getCalculationScale(), props.getRoundingMode());
        BigDecimal tax = gross.subtract(net);

        return new CorrectionAmounts(money(net), money(tax), gross, rateValue);
    }

    private BigDecimal resolveSingleVatRateValueOrThrow(List<FiscalDocumentLine> lines) {
        if (lines == null || lines.isEmpty()) {
            // sem linhas: assumir sem imposto
            return BigDecimal.ZERO;
        }
        BigDecimal found = null;
        for (FiscalDocumentLine l : lines) {
            if (l == null) continue;
            BigDecimal v = l.getTaxRateValue();
            if (v == null) v = BigDecimal.ZERO;
            v = v.stripTrailingZeros();
            if (found == null) {
                found = v;
            } else if (found.compareTo(v) != 0) {
                throw new BusinessException("Múltiplas taxas no documento original; requer linhas manuais.");
            }
        }
        return found != null ? found : BigDecimal.ZERO;
    }

    private BigDecimal money(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO.setScale(props.getMonetaryScale(), props.getRoundingMode());
        return v.setScale(props.getMonetaryScale(), props.getRoundingMode());
    }

    public record CorrectionAmounts(BigDecimal netAmount,
                                   BigDecimal taxAmount,
                                   BigDecimal totalAmount,
                                   BigDecimal taxRateValue) {}
}

