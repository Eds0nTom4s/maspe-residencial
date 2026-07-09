package com.restaurante.billing.dto.request;

import com.restaurante.model.enums.BillingInterval;
import com.restaurante.model.enums.BillingPlanStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateBillingPlanRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;
    private BillingPlanStatus status = BillingPlanStatus.ACTIVE;
    private BillingInterval billingInterval = BillingInterval.MONTHLY;
    private String currency = "AOA";

    private BigDecimal basePrice = BigDecimal.ZERO;
    private Long includedTransactions = 0L;
    private BigDecimal overagePricePerTransaction = BigDecimal.ZERO;
    private BigDecimal transactionFeePercentage = BigDecimal.ZERO;
    private BigDecimal minimumMonthlyFee = BigDecimal.ZERO;
}

