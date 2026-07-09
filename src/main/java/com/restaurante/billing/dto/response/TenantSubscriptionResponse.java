package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.TenantSubscriptionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantSubscriptionResponse {
    private Long id;
    private Long tenantId;
    private Long billingPlanId;
    private TenantSubscriptionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Integer billingAnchorDay;
    private String currency;
}

