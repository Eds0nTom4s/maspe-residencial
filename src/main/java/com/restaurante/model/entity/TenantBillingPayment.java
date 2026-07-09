package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantBillingPaymentMethod;
import com.restaurante.model.enums.TenantBillingPaymentStatus;
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
@Table(name = "tenant_billing_payments", indexes = {
        @Index(name = "uq_tenant_billing_payments_number", columnList = "tenant_id, payment_number", unique = true),
        @Index(name = "idx_tenant_billing_payments_invoice", columnList = "tenant_id, invoice_id, status"),
        @Index(name = "idx_tenant_billing_payments_cycle", columnList = "tenant_id, billing_cycle_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantBillingPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private TenantBillingInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_cycle_id")
    private BillingCycle billingCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private TenantSubscription subscription;

    @Column(name = "payment_number", nullable = false, length = 80)
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantBillingPaymentStatus status = TenantBillingPaymentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 60)
    private TenantBillingPaymentMethod paymentMethod = TenantBillingPaymentMethod.OTHER;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "reference", length = 160)
    private String reference;

    @Column(name = "proof_reference", length = 160)
    private String proofReference;

    @Column(name = "external_transaction_id", length = 160)
    private String externalTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedByUser;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}

