package com.restaurante.model.entity;

import com.restaurante.model.enums.UsageAggregationStatus;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_aggregations", indexes = {
        @Index(name = "idx_usage_aggregations_tenant_metric_period", columnList = "tenant_id, metric_code, period_start, period_end"),
        @Index(name = "idx_usage_aggregations_tenant_cycle", columnList = "tenant_id, billing_cycle_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UsageAggregation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private TenantSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_cycle_id")
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_code", nullable = false, length = 80)
    private UsageMetricCode metricCode;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "quantity_total", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityTotal = BigDecimal.ZERO;

    @Column(name = "amount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountTotal = BigDecimal.ZERO;

    @Column(name = "billable_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal billableQuantity = BigDecimal.ZERO;

    @Column(name = "included_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal includedQuantity = BigDecimal.ZERO;

    @Column(name = "overage_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal overageQuantity = BigDecimal.ZERO;

    @Column(name = "calculated_charge_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal calculatedChargeAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AOA";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UsageAggregationStatus status = UsageAggregationStatus.DRAFT;
}

