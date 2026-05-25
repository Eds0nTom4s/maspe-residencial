package com.restaurante.txevidence.dto.response;

import com.restaurante.model.enums.TransactionEvidenceAlgorithm;
import com.restaurante.model.enums.TransactionEvidenceEventStatus;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.model.enums.TransactionEvidenceVerificationStatus;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class TransactionEvidenceEventResponse {
    Long id;
    Long ledgerSequence;
    String eventType;
    TransactionEvidenceSourceModule sourceModule;
    String sourceEntityType;
    Long sourceEntityId;
    Long sourceEventId;
    LocalDateTime occurredAt;
    LocalDateTime recordedAt;
    String idempotencyKey;
    String canonicalPayloadVersion;
    String canonicalPayloadHash;
    String previousEventHash;
    String eventHash;
    String keyVersion;
    TransactionEvidenceAlgorithm algorithm;
    TransactionEvidenceEventStatus status;
    TransactionEvidenceVerificationStatus verificationStatus;
    String canonicalPayloadJson; // only for detail views
}

