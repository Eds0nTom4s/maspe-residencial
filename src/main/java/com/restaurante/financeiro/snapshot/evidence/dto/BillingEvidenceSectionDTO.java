package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BillingEvidenceSectionDTO {
    private LocalDateTime generatedAt;
    private Long tenantId;

    private Long subscriptionId;
    private Long billingCycleId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private Integer totalUsageEvents;

    private BigDecimal billableTransactions;
    private BigDecimal includedTransactions;
    private BigDecimal overageTransactions;

    private BigDecimal basePrice;
    private BigDecimal usageChargeAmount;
    private BigDecimal totalBillingAmount;

    private Long invoiceId;
    private String invoiceStatus;

    // Prompt 48: payments/collection
    private Integer totalInvoices;
    private Integer issuedInvoices;
    private Integer paidInvoices;
    private Integer partiallyPaidInvoices;
    private Integer overdueInvoices;
    private Integer cancelledInvoices;

    private BigDecimal totalInvoicedAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal totalOutstandingAmount;
    private BigDecimal totalOverdueAmount;

    private String collectionStatus;
    private LocalDateTime gracePeriodEndsAt;

    private List<BillingEvidencePaymentItemDTO> billingPayments;

    private List<String> warnings;
    private List<BillingEvidenceUsageItemDTO> usageAggregations;
    private List<BillingEvidenceInvoiceItemDTO> invoiceLines;
}
