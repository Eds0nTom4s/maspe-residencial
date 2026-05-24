package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalAdjustmentAssessmentStatus;
import com.restaurante.model.enums.FiscalAdjustmentImpactType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FiscalAdjustmentAssessmentResponse {
    private Long id;
    private Long tenantId;
    private Long caixaOperadorAdjustmentId;
    private Long caixaOperadorDivergenceId;
    private Long caixaOperadorSessionId;
    private Long turnoOperacionalId;
    private Long unidadeAtendimentoId;
    private Long originalFiscalDocumentId;
    private FiscalAdjustmentAssessmentStatus status;
    private FiscalAdjustmentImpactType impactType;
    private String decisionReason;
    private Long assessedByUserId;
    private LocalDateTime assessedAt;
    private Long correctionFiscalDocumentId;
}

