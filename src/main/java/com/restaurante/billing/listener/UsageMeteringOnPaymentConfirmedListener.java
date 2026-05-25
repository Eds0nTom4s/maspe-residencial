package com.restaurante.billing.listener;

import com.restaurante.billing.config.BillingProperties;
import com.restaurante.billing.service.UsageMeteringService;
import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class UsageMeteringOnPaymentConfirmedListener {

    private final BillingProperties props;
    private final UsageMeteringService meteringService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedForFiscalIssueEvent event) {
        if (event == null) return;
        if (!props.isEnabled()) return;
        try {
            meteringService.recordPaymentConfirmed(
                    event.tenantId(),
                    event.unidadeAtendimentoIdOrNull(),
                    event.pagamentoId(),
                    null
            );
        } catch (Exception ignored) {
            // Metering não deve quebrar pagamento confirmado (MVP).
        }
    }
}

