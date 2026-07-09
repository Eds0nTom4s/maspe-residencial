package com.restaurante.billing.dto.request;

import com.restaurante.model.enums.TenantSubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertTenantSubscriptionRequest {
    @NotNull
    private Long billingPlanId;
    private TenantSubscriptionStatus status = TenantSubscriptionStatus.TRIALING;
    private Integer billingAnchorDay = 1;
}

