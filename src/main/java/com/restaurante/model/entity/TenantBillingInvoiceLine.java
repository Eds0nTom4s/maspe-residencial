package com.restaurante.model.entity;

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
@Table(name = "tenant_billing_invoice_lines", indexes = {
        @Index(name = "idx_tenant_billing_invoice_lines_invoice", columnList = "tenant_billing_invoice_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantBillingInvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_billing_invoice_id", nullable = false)
    private TenantBillingInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_code", nullable = false, length = 80)
    private UsageMetricCode metricCode;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "included_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal includedQuantity = BigDecimal.ZERO;

    @Column(name = "overage_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal overageQuantity = BigDecimal.ZERO;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;
}

