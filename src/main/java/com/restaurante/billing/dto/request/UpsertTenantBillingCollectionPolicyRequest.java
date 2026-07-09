package com.restaurante.billing.dto.request;

import com.restaurante.model.enums.TenantBillingCollectionPolicyStatus;
import com.restaurante.model.enums.TenantBillingSuspensionMode;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpsertTenantBillingCollectionPolicyRequest {
    @Min(0)
    private Integer gracePeriodDays;
    @Min(0)
    private Integer overdueWarningDays;
    private Boolean autoMarkOverdue;

    private Boolean allowOperationWhenOverdue;
    private Boolean allowOperationWhenSuspended;

    private TenantBillingSuspensionMode suspensionMode;
    @Min(0)
    private Integer suspensionAfterDays;

    private Boolean restrictNewOrders;
    private Boolean restrictNewDevices;
    private Boolean restrictAdminAccess;

    private TenantBillingCollectionPolicyStatus status;
}

