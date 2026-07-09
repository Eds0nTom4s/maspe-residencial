package com.restaurante.delivery.listener;

import com.restaurante.delivery.service.DeliveryJobService;
import com.restaurante.delivery.service.OrderFulfillmentService;
import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.model.entity.OrderFulfillment;
import com.restaurante.model.enums.FulfillmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryOnPaymentConfirmedListener {

    private final OrderFulfillmentService orderFulfillmentService;
    private final DeliveryJobService deliveryJobService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedForFiscalIssueEvent event) {
        try {
            Long tenantId = event.tenantId();
            Long pedidoId = event.pedidoId();

            OrderFulfillment fulfillment = orderFulfillmentService.getOrNull(tenantId, pedidoId);
            if (fulfillment != null && fulfillment.getFulfillmentType() == FulfillmentType.CONSUMA_NETWORK_DELIVERY) {
                log.info("Iniciando DeliveryJob para Pedido {} do Tenant {}", pedidoId, tenantId);
                deliveryJobService.createJobForFulfillment(tenantId, pedidoId);
            }
        } catch (Exception e) {
            log.error("Erro ao iniciar DeliveryJob de forma assincrona/pos-commit. O pagamento nao foi revertido.", e);
        }
    }
}
