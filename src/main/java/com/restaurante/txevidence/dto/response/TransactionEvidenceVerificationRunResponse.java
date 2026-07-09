package com.restaurante.txevidence.dto.response;

import com.restaurante.model.enums.TransactionEvidenceVerificationRunStatus;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class TransactionEvidenceVerificationRunResponse {
    Long id;
    LocalDateTime periodStart;
    LocalDateTime periodEnd;
    TransactionEvidenceVerificationRunStatus status;
    Integer checkedEventsCount;
    Integer invalidEventsCount;
    Integer brokenChainCount;
    Integer sequenceGapCount;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    String reportHash;
}

