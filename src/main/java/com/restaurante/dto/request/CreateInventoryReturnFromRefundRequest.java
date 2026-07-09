package com.restaurante.dto.request;

import com.restaurante.inventory.event.PaymentRefundedForInventoryReturnEvent;
import com.restaurante.model.enums.InventoryRestockPolicy;
import com.restaurante.model.enums.InventoryReturnReasonCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateInventoryReturnFromRefundRequest {
    @NotNull
    private Long pedidoId;

    private Long pagamentoId;

    private String refundReferenceId;

    private BigDecimal refundAmount;

    @NotNull
    private PaymentRefundedForInventoryReturnEvent.RefundType refundType;

    private InventoryReturnReasonCategory reasonCategory = InventoryReturnReasonCategory.PAYMENT_REFUNDED;

    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull
        private Long pedidoItemId;
        @NotNull
        private BigDecimal quantityReturned;
        private InventoryRestockPolicy restockPolicy;
    }
}

