package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.model.enums.FiscalAdjustmentAssessmentStatus;
import com.restaurante.model.enums.FiscalAdjustmentImpactType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaxEvidenceAssessmentItemDTO {
    private Long assessmentId;
    private Long caixaAdjustmentId;
    private Long originalFiscalDocumentId;
    private FiscalAdjustmentAssessmentStatus status;
    private FiscalAdjustmentImpactType impactType;
    private LocalDateTime assessedAt;
    private String assessmentHash;
}

