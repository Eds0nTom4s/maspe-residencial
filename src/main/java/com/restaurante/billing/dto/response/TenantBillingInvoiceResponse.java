package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.TenantBillingInvoiceStatus;
import com.restaurante.model.enums.TenantBillingCollectionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TenantBillingInvoiceResponse {
    private Long id;
    private Long tenantId;
    private Long subscriptionId;
    private Long billingCycleId;
    private String invoiceNumber;
    private TenantBillingInvoiceStatus status;
    private String currency;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal outstandingAmount;
    private LocalDateTime lastPaymentAt;
    private LocalDateTime overdueAt;
    private LocalDateTime gracePeriodEndsAt;
    private TenantBillingCollectionStatus collectionStatus;
    private LocalDateTime issuedAt;
    private LocalDateTime dueAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private String notes;
    private List<TenantBillingInvoiceLineResponse> lines;
}
