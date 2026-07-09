package com.restaurante.fiscal.official.listener;

import com.restaurante.fiscal.official.config.OfficialFiscalProperties;
import com.restaurante.fiscal.official.event.FiscalDocumentIssuedForOfficialSubmissionEvent;
import com.restaurante.fiscal.official.service.OfficialFiscalSubmissionService;
import com.restaurante.model.enums.OfficialFiscalSubmissionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class OfficialFiscalSubmissionOnFiscalDocumentIssuedListener {

    private final OfficialFiscalProperties props;
    private final OfficialFiscalSubmissionService submissionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssued(FiscalDocumentIssuedForOfficialSubmissionEvent ev) {
        if (ev == null || ev.tenantId() == null || ev.fiscalDocumentId() == null) return;
        if (!props.isEnabled()) return;
        // criação automática só quando explicitamente habilitado por profile (submissionMode)
        submissionService.createSubmissionIfAutoMode(ev.tenantId(), ev.fiscalDocumentId(), OfficialFiscalSubmissionMode.AUTO_AFTER_INTERNAL_ISSUE);
    }
}

