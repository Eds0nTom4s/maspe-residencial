package com.restaurante.model.entity;

import com.restaurante.model.enums.DeliveryPricingPolicyScope;
import com.restaurante.model.enums.DeliveryPricingPolicyStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_pricing_policies")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DeliveryPricingPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 40)
    private DeliveryPricingPolicyScope scope = DeliveryPricingPolicyScope.GLOBAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DeliveryPricingPolicyStatus status = DeliveryPricingPolicyStatus.ACTIVE;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "AOA";

    @Column(name = "base_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseFeeAmount = new BigDecimal("700.00");

    @Column(name = "per_km_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal perKmFeeAmount = new BigDecimal("250.00");

    @Column(name = "minimum_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumFeeAmount = new BigDecimal("1000.00");

    @Column(name = "maximum_fee_amount", precision = 19, scale = 2)
    private BigDecimal maximumFeeAmount;

    @Column(name = "consuma_commission_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal consumaCommissionPercentage = new BigDecimal("20.00");

    @Column(name = "courier_share_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal courierSharePercentage = new BigDecimal("80.00");

    @Column(name = "peak_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal peakMultiplier = new BigDecimal("1.00");

    @Column(name = "fragile_package_surcharge", nullable = false, precision = 19, scale = 2)
    private BigDecimal fragilePackageSurcharge = BigDecimal.ZERO;

    @Column(name = "large_package_surcharge", nullable = false, precision = 19, scale = 2)
    private BigDecimal largePackageSurcharge = BigDecimal.ZERO;

    @Column(name = "night_surcharge", nullable = false, precision = 19, scale = 2)
    private BigDecimal nightSurcharge = BigDecimal.ZERO;

    @Column(name = "free_delivery_threshold", precision = 19, scale = 2)
    private BigDecimal freeDeliveryThreshold;

    @Column(name = "tenant_subsidy_enabled", nullable = false)
    private Boolean tenantSubsidyEnabled = false;

    @Column(name = "customer_pays_delivery", nullable = false)
    private Boolean customerPaysDelivery = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;
}
