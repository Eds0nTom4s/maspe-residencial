package com.restaurante.model.entity;

import com.restaurante.model.enums.UsageMetricAggregationType;
import com.restaurante.model.enums.UsageMetricCode;
import com.restaurante.model.enums.UsageMetricStatus;
import com.restaurante.model.enums.UsageMetricUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usage_metrics", indexes = {
        @Index(name = "uq_usage_metrics_code", columnList = "code", unique = true),
        @Index(name = "idx_usage_metrics_billable", columnList = "billable")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UsageMetric extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 80)
    private UsageMetricCode code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 40)
    private UsageMetricUnit unit = UsageMetricUnit.COUNT;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_type", nullable = false, length = 40)
    private UsageMetricAggregationType aggregationType = UsageMetricAggregationType.COUNT;

    @Column(name = "billable", nullable = false)
    private boolean billable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UsageMetricStatus status = UsageMetricStatus.ACTIVE;
}

