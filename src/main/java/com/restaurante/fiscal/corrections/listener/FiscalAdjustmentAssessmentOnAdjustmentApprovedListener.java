package com.restaurante.fiscal.corrections.listener;

import com.restaurante.fiscal.corrections.event.CaixaOperadorAdjustmentApprovedForFiscalAssessmentEvent;
import com.restaurante.fiscal.corrections.service.FiscalAdjustmentAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class FiscalAdjustmentAssessmentOnAdjustmentApprovedListener {

    private final FiscalAdjustmentAssessmentService assessmentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovedAdjustment(CaixaOperadorAdjustmentApprovedForFiscalAssessmentEvent event) {
        if (event == null || event.tenantId() == null || event.adjustmentId() == null) return;
        assessmentService.createAssessmentForApprovedAdjustment(event.tenantId(), event.adjustmentId());
    }
}

