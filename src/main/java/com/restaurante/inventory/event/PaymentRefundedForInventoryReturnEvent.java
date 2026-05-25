package com.restaurante.inventory.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRefundedForInventoryReturnEvent(
        Long tenantId,
        Long pedidoId,
        Long pagamentoId,
        String refundReferenceId,
        BigDecimal refundAmount,
        RefundType refundType,
        LocalDateTime refundedAt,
        String source
) {
    public enum RefundType {
        FULL_REFUND,
        PARTIAL_REFUND,
        DUPLICATE_PAYMENT_REVERSAL,
        ADMIN_REFUND
    }
}

