package com.restaurante.billing.dto.request;

import com.restaurante.model.enums.UsageAdjustmentType;
import com.restaurante.model.enums.UsageMetricCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateUsageAdjustmentRequest {
    @NotNull
    private UsageMetricCode metricCode;
    @NotNull
    private UsageAdjustmentType adjustmentType;
    private BigDecimal quantityDelta = BigDecimal.ZERO;
    private BigDecimal amountDelta = BigDecimal.ZERO;
    private String reason;
    private String referenceType;
    private Long referenceId;
    private Long originalUsageEventId;
}

