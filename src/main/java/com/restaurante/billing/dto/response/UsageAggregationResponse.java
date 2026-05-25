package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.UsageAggregationStatus;
import com.restaurante.model.enums.UsageMetricCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UsageAggregationResponse {
    private Long id;
    private Long tenantId;
    private Long subscriptionId;
    private Long billingCycleId;
    private UsageMetricCode metricCode;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private BigDecimal quantityTotal;
    private BigDecimal amountTotal;
    private BigDecimal billableQuantity;
    private BigDecimal includedQuantity;
    private BigDecimal overageQuantity;
    private BigDecimal calculatedChargeAmount;
    private String currency;
    private UsageAggregationStatus status;
}

