package com.restaurante.fiscal.corrections.event;

public record CaixaOperadorAdjustmentApprovedForFiscalAssessmentEvent(
        Long tenantId,
        Long adjustmentId
) {
}

