package com.restaurante.fiscal.corrections.event;

import com.restaurante.model.enums.FiscalCorrectionSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FiscalCreditNoteIssuedForInventoryReturnEvent(
        Long tenantId,
        Long correctionFiscalDocumentId,
        Long originalFiscalDocumentId,
        Long pedidoId,
        Long pagamentoId,
        BigDecimal amount,
        LocalDateTime issuedAt,
        FiscalCorrectionSource correctionSource
) {
}

