package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.TenantBillingPaymentMethod;
import com.restaurante.model.enums.TenantBillingPaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TenantBillingPaymentResponse {
    private Long id;
    private Long tenantId;
    private Long invoiceId;
    private Long billingCycleId;
    private Long subscriptionId;

    private String paymentNumber;
    private TenantBillingPaymentStatus status;
    private TenantBillingPaymentMethod paymentMethod;

    private BigDecimal amount;
    private String currency;

    private LocalDateTime paidAt;
    private LocalDateTime receivedAt;
    private LocalDateTime confirmedAt;

    private String reference;
    private String proofReference;
    private String notes;
}

