package com.restaurante.model.entity;

import com.restaurante.model.enums.BillingInterval;
import com.restaurante.model.enums.BillingPlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "billing_plans", indexes = {
        @Index(name = "uq_billing_plans_code", columnList = "code", unique = true),
        @Index(name = "idx_billing_plans_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BillingPlan extends BaseEntity {

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingPlanStatus status = BillingPlanStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 30)
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "base_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(name = "included_transactions", nullable = false)
    private Long includedTransactions = 0L;

    @Column(name = "included_devices", nullable = false)
    private Long includedDevices = 0L;

    @Column(name = "included_units", nullable = false)
    private Long includedUnits = 0L;

    @Column(name = "overage_price_per_transaction", nullable = false, precision = 19, scale = 4)
    private BigDecimal overagePricePerTransaction = BigDecimal.ZERO;

    @Column(name = "overage_price_per_device", nullable = false, precision = 19, scale = 4)
    private BigDecimal overagePricePerDevice = BigDecimal.ZERO;

    @Column(name = "overage_price_per_unit", nullable = false, precision = 19, scale = 4)
    private BigDecimal overagePricePerUnit = BigDecimal.ZERO;

    @Column(name = "transaction_fee_percentage", nullable = false, precision = 9, scale = 6)
    private BigDecimal transactionFeePercentage = BigDecimal.ZERO;

    @Column(name = "minimum_monthly_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumMonthlyFee = BigDecimal.ZERO;
}

