package com.restaurante.billing.dto.request;

import com.restaurante.model.enums.TenantBillingPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecordTenantBillingPaymentRequest {

    @NotNull
    @DecimalMin("0.0001")
    private BigDecimal amount;

    @NotNull
    private String currency;

    @NotNull
    private TenantBillingPaymentMethod paymentMethod;

    private LocalDateTime paidAt;
    private String reference;
    private String proofReference;
    private String notes;
}

