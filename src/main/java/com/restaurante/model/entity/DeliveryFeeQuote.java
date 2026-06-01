package com.restaurante.model.entity;

import com.restaurante.model.enums.DeliveryFeeQuoteStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_fee_quotes")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DeliveryFeeQuote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_fulfillment_id")
    private OrderFulfillment orderFulfillment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_job_id")
    private DeliveryJob deliveryJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_policy_id", nullable = false)
    private DeliveryPricingPolicy pricingPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DeliveryFeeQuoteStatus status = DeliveryFeeQuoteStatus.DRAFT;

    @Column(name = "distance_km", nullable = false, precision = 9, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "AOA";

    @Column(name = "base_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseFeeAmount;

    @Column(name = "distance_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal distanceFeeAmount;

    @Column(name = "surcharge_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal surchargeAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tenant_subsidy_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal tenantSubsidyAmount = BigDecimal.ZERO;

    @Column(name = "customer_pays_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal customerPaysAmount;

    @Column(name = "final_delivery_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalDeliveryFeeAmount;

    @Column(name = "courier_earning_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal courierEarningAmount;

    @Column(name = "consuma_commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal consumaCommissionAmount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
}
