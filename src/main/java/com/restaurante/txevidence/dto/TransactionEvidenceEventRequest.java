package com.restaurante.txevidence.dto;

import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TransactionEvidenceEventRequest {
    private Long tenantId;
    private String eventType;
    private TransactionEvidenceSourceModule sourceModule;
    private String sourceEntityType;
    private Long sourceEntityId;
    private Long sourceEventId;
    private LocalDateTime occurredAt;
    private String idempotencyKey;
    private Map<String, Object> payloadFields;
    private Map<String, Object> metadataFields;
}

