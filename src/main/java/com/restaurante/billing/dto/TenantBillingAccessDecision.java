package com.restaurante.billing.dto;

import com.restaurante.model.enums.TenantBillingCollectionStatus;
import lombok.Data;

@Data
public class TenantBillingAccessDecision {
    private boolean allowed;
    private boolean warningOnly;
    private String blockReason;
    private TenantBillingCollectionStatus collectionStatus;
    private String messageCode;
}

