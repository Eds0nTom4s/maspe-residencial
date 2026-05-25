package com.restaurante.model.entity;

import com.restaurante.model.enums.UsageEventStatus;
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
@Table(name = "usage_events", indexes = {
        @Index(name = "uq_usage_events_idempotency", columnList = "tenant_id, idempotency_key", unique = true),
        @Index(name = "idx_usage_events_tenant_metric_time", columnList = "tenant_id, metric_code, occurred_at"),
        @Index(name = "idx_usage_events_source_entity", columnList = "tenant_id, source_entity_type, source_entity_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UsageEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_code", nullable = false, length = 80)
    private UsageMetricCode metricCode;

    @Column(name = "source_event_type", length = 120)
    private String sourceEventType;

    @Column(name = "source_entity_type", length = 120)
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id")
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_device_id")
    private DispositivoOperacional operationalDevice;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UsageEventStatus status = UsageEventStatus.RECORDED;
}

