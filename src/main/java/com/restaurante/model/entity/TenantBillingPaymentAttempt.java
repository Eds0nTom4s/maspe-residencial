package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantBillingPaymentAttemptStatus;
import com.restaurante.model.enums.TenantBillingPaymentMethod;
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
@Table(name = "tenant_billing_payment_attempts", indexes = {
        @Index(name = "idx_tenant_billing_payment_attempts_invoice", columnList = "tenant_id, invoice_id, attempted_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantBillingPaymentAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private TenantBillingInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private TenantBillingPayment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantBillingPaymentAttemptStatus status = TenantBillingPaymentAttemptStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 60)
    private TenantBillingPaymentMethod method = TenantBillingPaymentMethod.OTHER;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "external_reference", length = 160)
    private String externalReference;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}

