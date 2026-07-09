package com.restaurante.model.entity;

import com.restaurante.model.enums.DeliveryFeePaymentStatus;
import com.restaurante.model.enums.DeliveryJobStatus;
import com.restaurante.model.enums.CourierEarningStatus;
import com.restaurante.model.enums.CourierSettlementStatus;
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
@Table(name = "delivery_jobs", indexes = {
        @Index(name = "uq_delivery_job_pedido", columnList = "tenant_id, pedido_id", unique = true),
        @Index(name = "idx_delivery_job_status", columnList = "tenant_id, status, requested_at"),
        @Index(name = "idx_delivery_job_courier", columnList = "courier_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DeliveryJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_fulfillment_id", nullable = false)
    private OrderFulfillment orderFulfillment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private CourierProfile courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 80)
    private DeliveryJobStatus status = DeliveryJobStatus.CREATED;

    @Column(name = "pickup_address_text", columnDefinition = "text")
    private String pickupAddressText;

    @Column(name = "pickup_latitude", precision = 10, scale = 6)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 10, scale = 6)
    private BigDecimal pickupLongitude;

    @Column(name = "delivery_address_text", columnDefinition = "text")
    private String deliveryAddressText;

    @Column(name = "delivery_latitude", precision = 10, scale = 6)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 10, scale = 6)
    private BigDecimal deliveryLongitude;

    @Column(name = "estimated_distance_km", precision = 9, scale = 3)
    private BigDecimal estimatedDistanceKm;

    @Column(name = "estimated_delivery_fee", precision = 19, scale = 2)
    private BigDecimal estimatedDeliveryFee;

    @Column(name = "final_delivery_fee", precision = 19, scale = 2)
    private BigDecimal finalDeliveryFee;

    @Column(name = "delivery_fee_currency", length = 10)
    private String deliveryFeeCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_fee_quote_id")
    private DeliveryFeeQuote deliveryFeeQuote;

    @Column(name = "customer_pays_delivery_amount", precision = 19, scale = 2)
    private BigDecimal customerPaysDeliveryAmount;

    @Column(name = "tenant_subsidy_amount", precision = 19, scale = 2)
    private BigDecimal tenantSubsidyAmount;

    @Column(name = "courier_earning_amount", precision = 19, scale = 2)
    private BigDecimal courierEarningAmount;

    @Column(name = "consuma_commission_amount", precision = 19, scale = 2)
    private BigDecimal consumaCommissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_status", nullable = false, length = 40)
    private CourierEarningStatus earningStatus = CourierEarningStatus.NOT_EARNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 40)
    private CourierSettlementStatus settlementStatus = CourierSettlementStatus.NOT_READY;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_fee_payment_status", nullable = false, length = 40)
    private DeliveryFeePaymentStatus deliveryFeePaymentStatus = DeliveryFeePaymentStatus.NOT_CHARGED;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;
}

