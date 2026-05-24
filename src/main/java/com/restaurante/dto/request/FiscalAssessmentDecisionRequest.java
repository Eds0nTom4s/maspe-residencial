package com.restaurante.dto.request;

import lombok.Data;

@Data
public class FiscalAssessmentDecisionRequest {
    private String reason;
    private Long originalFiscalDocumentId;
}

