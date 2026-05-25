package com.restaurante.model.entity;

import com.restaurante.model.enums.UsageAdjustmentType;
import com.restaurante.model.enums.UsageMetricCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "usage_adjustments", indexes = {
        @Index(name = "idx_usage_adjustments_tenant_created_at", columnList = "tenant_id, created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UsageAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_usage_event_id")
    private UsageEvent originalUsageEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 40)
    private UsageAdjustmentType adjustmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_code", nullable = false, length = 80)
    private UsageMetricCode metricCode;

    @Column(name = "quantity_delta", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityDelta = BigDecimal.ZERO;

    @Column(name = "amount_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDelta = BigDecimal.ZERO;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "reference_type", length = 120)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;
}
