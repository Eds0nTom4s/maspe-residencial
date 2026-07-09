package com.restaurante.inventory.listener;

import com.restaurante.exception.BusinessException;
import com.restaurante.inventory.event.PaymentRefundedForInventoryReturnEvent;
import com.restaurante.inventory.service.InventoryReturnService;
import com.restaurante.inventory.service.TenantInventoryPolicyService;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.TenantInventoryPolicy;
import com.restaurante.model.enums.InventoryRestockPolicy;
import com.restaurante.model.enums.InventoryReturnReasonCategory;
import com.restaurante.model.enums.InventoryReturnSource;
import com.restaurante.model.enums.InventoryReturnType;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InventoryReturnOnPaymentRefundedListener {

    private final PedidoRepository pedidoRepository;
    private final InventoryReturnService returnService;
    private final TenantInventoryPolicyService policyService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefunded(PaymentRefundedForInventoryReturnEvent event) {
        if (event == null || event.tenantId() == null || event.pedidoId() == null) return;
        Pedido pedido = pedidoRepository.findById(event.pedidoId()).orElse(null);
        if (pedido == null || pedido.getTenant() == null || !pedido.getTenant().getId().equals(event.tenantId())) return;

        TenantInventoryPolicy policy = policyService.getOrCreateDefault(pedido.getTenant());
        if (policy == null || !Boolean.TRUE.equals(policy.getAutoCreateReturnOnRefund())) return;

        if (event.refundType() == PaymentRefundedForInventoryReturnEvent.RefundType.DUPLICATE_PAYMENT_REVERSAL) {
            return;
        }

        List<InventoryReturnService.RequestedReturnLine> lines = new ArrayList<>();
        if (pedido.getItens() != null && event.refundType() == PaymentRefundedForInventoryReturnEvent.RefundType.FULL_REFUND) {
            for (var it : pedido.getItens()) {
                if (it == null || it.getId() == null) continue;
                int qty = it.getQuantidade();
                if (qty <= 0) continue;
                lines.add(new InventoryReturnService.RequestedReturnLine(it.getId(), BigDecimal.valueOf(qty),
                        policy.getDefaultRefundRestockPolicy() != null ? policy.getDefaultRefundRestockPolicy() : InventoryRestockPolicy.MANUAL_REVIEW));
            }
        }

        if (lines.isEmpty()) return;

        try {
            returnService.createReturn(
                    event.tenantId(),
                    event.pedidoId(),
                    InventoryReturnType.PAYMENT_REFUND,
                    InventoryReturnReasonCategory.PAYMENT_REFUNDED,
                    "Criado a partir de evento de refund (placeholder)",
                    lines,
                    InventoryReturnSource.SYSTEM_REFUND_EVENT,
                    null
            );
        } catch (BusinessException ignored) {
        }
    }
}
