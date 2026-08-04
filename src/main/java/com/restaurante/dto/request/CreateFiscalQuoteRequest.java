package com.restaurante.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateFiscalQuoteRequest(
        @Size(max = 255) String customerName,
        @Size(max = 40) String customerTaxpayerNumber,
        @NotEmpty @Size(max = 100) List<@Valid Line> lines
) {
    public record Line(
            @NotBlank @Size(max = 255) String description,
            @NotNull @Min(1) Integer quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal taxRatePercent
    ) {}
}
