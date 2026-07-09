package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillingEvidenceInvoiceItemDTO {
    private Long invoiceId;
    private String invoiceNumber;
    private String status;
    private BigDecimal subtotalAmount;
    private BigDecimal totalAmount;
    private LocalDateTime issuedAt;
    private String invoiceHash;
}

