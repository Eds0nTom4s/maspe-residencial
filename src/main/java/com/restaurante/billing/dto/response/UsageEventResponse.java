package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.UsageEventStatus;
import com.restaurante.model.enums.UsageMetricCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UsageEventResponse {
    private Long id;
    private Long tenantId;
    private UsageMetricCode metricCode;
    private String sourceEventType;
    private String sourceEntityType;
    private Long sourceEntityId;
    private String idempotencyKey;
    private LocalDateTime occurredAt;
    private BigDecimal quantity;
    private BigDecimal amount;
    private String currency;
    private UsageEventStatus status;
}

