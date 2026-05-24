package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.model.enums.FiscalCorrectionSource;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TaxEvidenceCorrectionDocumentItemDTO {
    private Long correctionDocumentId;
    private Long originalFiscalDocumentId;
    private Long assessmentId;
    private Long caixaAdjustmentId;
    private FiscalDocumentType documentType;
    private FiscalDocumentStatus status;
    private String documentNumber;
    private String series;
    private LocalDateTime issuedAt;
    private FiscalCorrectionSource correctionSource;
    private String correctionReasonHash;
    private BigDecimal netAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String documentHash;
}

