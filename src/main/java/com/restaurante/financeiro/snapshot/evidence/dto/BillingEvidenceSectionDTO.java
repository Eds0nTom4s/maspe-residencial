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

    private List<String> warnings;
    private List<BillingEvidenceUsageItemDTO> usageAggregations;
    private List<BillingEvidenceInvoiceItemDTO> invoiceLines;
}

