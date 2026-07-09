package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.TenantBillingCollectionStatus;
import com.restaurante.model.enums.TenantBillingSuspensionMode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TenantBillingCollectionStatusResponse {
    private Long tenantId;
    private TenantBillingCollectionStatus collectionStatus;
    private Integer overdueInvoices;
    private BigDecimal totalOutstandingAmount;
    private LocalDateTime gracePeriodEndsAt;
    private TenantBillingSuspensionMode suspensionMode;
    private boolean restrictNewOrders;
    private boolean restrictNewDevices;
    private boolean restrictAdminAccess;
    private String messageCode;
}

