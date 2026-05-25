package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.BillingCycleStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BillingCycleResponse {
    private Long id;
    private Long tenantId;
    private Long subscriptionId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private BillingCycleStatus status;
    private LocalDateTime usageFinalizedAt;
    private LocalDateTime invoiceGeneratedAt;
}

