package com.restaurante.billing.dto.response;

import com.restaurante.model.enums.BillingInterval;
import com.restaurante.model.enums.BillingPlanStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillingPlanResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private BillingPlanStatus status;
    private BillingInterval billingInterval;
    private String currency;

    private BigDecimal basePrice;
    private Long includedTransactions;
    private BigDecimal overagePricePerTransaction;
    private BigDecimal transactionFeePercentage;
    private BigDecimal minimumMonthlyFee;
}

