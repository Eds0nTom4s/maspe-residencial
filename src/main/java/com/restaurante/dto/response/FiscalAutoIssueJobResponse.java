package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import com.restaurante.model.enums.FiscalAutoIssueTriggerType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FiscalAutoIssueJobResponse {
    private Long id;
    private Long tenantId;
    private Long unidadeAtendimentoId;
    private Long pedidoId;
    private Long pagamentoId;
    private Long sessaoConsumoId;
    private Long caixaOperadorSessionId;

    private FiscalAutoIssueSource source;
    private FiscalAutoIssueJobStatus status;
    private FiscalAutoIssueTriggerType triggerType;

    private int attemptCount;
    private int maxAttempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lastAttemptAt;

    private String lockedBy;
    private LocalDateTime lockedAt;

    private String errorCode;
    private String errorMessage;

    private Long fiscalDocumentId;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
}

