package com.restaurante.model.entity;

import com.restaurante.model.enums.BillingCycleStatus;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "billing_cycles", indexes = {
        @Index(name = "uq_billing_cycles_unique_period", columnList = "tenant_id, subscription_id, period_start, period_end", unique = true),
        @Index(name = "idx_billing_cycles_tenant_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BillingCycle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private TenantSubscription subscription;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingCycleStatus status = BillingCycleStatus.OPEN;

    @Column(name = "usage_finalized_at")
    private LocalDateTime usageFinalizedAt;

    @Column(name = "invoice_generated_at")
    private LocalDateTime invoiceGeneratedAt;
}

