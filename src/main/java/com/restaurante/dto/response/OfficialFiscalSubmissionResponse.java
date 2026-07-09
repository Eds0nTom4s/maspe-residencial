package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalAuthority;
import com.restaurante.model.enums.OfficialFiscalEnvironment;
import com.restaurante.model.enums.OfficialFiscalSubmissionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OfficialFiscalSubmissionResponse {
    private Long id;
    private Long tenantId;
    private Long fiscalDocumentId;
    private Long originalFiscalDocumentId;
    private String documentType;
    private OfficialFiscalSubmissionStatus status;
    private FiscalAuthority authority;
    private OfficialFiscalEnvironment environment;
    private String requestId;
    private String officialDocumentId;
    private String officialStatusCode;
    private String officialStatusMessage;
    private String payloadHash;
    private String signedPayloadHash;
    private LocalDateTime submittedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime rejectedAt;
    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime createdAt;
}

