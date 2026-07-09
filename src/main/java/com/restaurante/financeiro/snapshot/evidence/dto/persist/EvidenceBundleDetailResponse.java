package com.restaurante.financeiro.snapshot.evidence.dto.persist;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleDetailResponse {
    private Long bundleId;
    private Long tenantId;
    private Long turnoId;
    private Integer sequenceNumber;
    private String bundleVersion;
    private String bundleType;
    private String status;
    private LocalDateTime generatedAt;
    private Long generatedByUserId;

    private JsonNode bundle;
    private EvidenceBundleIntegrityResponse integridade;
    private EvidenceBundleChainResponse cadeiaCustodia;
    private EvidenceBundleRetentionResponse retencao;
    private EvidenceBundleVerificationResponse verification;
}

