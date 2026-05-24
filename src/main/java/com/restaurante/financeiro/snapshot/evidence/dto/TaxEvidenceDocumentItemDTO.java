package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TaxEvidenceDocumentItemDTO {
    private Long documentId;
    private String documentType;
    private String status;
    private String documentNumber;
    private String series;
    private LocalDateTime issuedAt;
    private Long pedidoId;
    private Long pagamentoId;
    private Long sessaoConsumoId;
    private BigDecimal subtotalAmount;
    private BigDecimal taxableAmount;
    private BigDecimal exemptAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String documentHash;
}

