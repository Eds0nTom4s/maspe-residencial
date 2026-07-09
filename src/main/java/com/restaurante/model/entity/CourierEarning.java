package com.restaurante.model.entity;

import com.restaurante.model.enums.CourierEarningStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "courier_earnings", indexes = {
        @Index(name = "idx_courier_earning_courier", columnList = "courier_id, status"),
        @Index(name = "idx_courier_earning_job", columnList = "delivery_job_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CourierEarning extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private CourierProfile courier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_job_id", nullable = false)
    private DeliveryJob deliveryJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CourierEarningStatus status = CourierEarningStatus.PENDING_DELIVERY;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "AOA";

    @Column(name = "delivery_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryFeeAmount;

    @Column(name = "courier_earning_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal courierEarningAmount;

    @Column(name = "consuma_commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal consumaCommissionAmount;

    @Column(name = "tenant_subsidy_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal tenantSubsidyAmount = BigDecimal.ZERO;

    @Column(name = "earned_at")
    private LocalDateTime earnedAt;

    @Column(name = "payable_at")
    private LocalDateTime payableAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "held_at")
    private LocalDateTime heldAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "dispute_reason", columnDefinition = "text")
    private String disputeReason;
}
