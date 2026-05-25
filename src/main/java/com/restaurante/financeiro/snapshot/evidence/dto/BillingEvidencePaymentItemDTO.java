package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillingEvidencePaymentItemDTO {
    private Long paymentId;
    private Long invoiceId;
    private String status;
    private String paymentMethod;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private String paymentHash;
}

