package com.restaurante.txevidence.dto.response;

import com.restaurante.model.enums.TransactionEvidenceVerificationIssueType;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class TransactionEvidenceVerificationIssueResponse {
    Long id;
    Long eventId;
    Long ledgerSequence;
    TransactionEvidenceVerificationIssueType issueType;
    String description;
    LocalDateTime detectedAt;
}

