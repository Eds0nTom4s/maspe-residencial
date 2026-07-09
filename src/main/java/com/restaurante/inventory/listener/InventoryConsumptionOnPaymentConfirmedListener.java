package com.restaurante.inventory.listener;

import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.inventory.service.InventoryConsumptionService;
import com.restaurante.model.enums.InventoryMovementSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class InventoryConsumptionOnPaymentConfirmedListener {

    private final InventoryConsumptionService consumptionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedForFiscalIssueEvent event) {
        InventoryMovementSource source = switch (event.source()) {
            case CASH_MANUAL_PAYMENT -> InventoryMovementSource.POS;
            case TPA_MANUAL_PAYMENT -> InventoryMovementSource.POS;
            case APPYPAY_CALLBACK -> InventoryMovementSource.SYSTEM;
            case APPYPAY_POLLING -> InventoryMovementSource.SYSTEM;
            case QR_PUBLIC_PAYMENT -> InventoryMovementSource.QR_PUBLIC;
            case ADMIN_MANUAL_TRIGGER -> InventoryMovementSource.ADMIN;
            case SYSTEM_BACKFILL -> InventoryMovementSource.SYSTEM;
            default -> InventoryMovementSource.SYSTEM;
        };
        consumptionService.consumeOnPaymentConfirmed(event.tenantId(), event.pedidoId(), event.pagamentoId(), source);
    }
}
