package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.UsageMetricCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TenantBillingInvoiceLineResponse {
    private Long id;
    private UsageMetricCode metricCode;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal includedQuantity;
    private BigDecimal overageQuantity;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}

