package com.restaurante.financeiro.snapshot.evidence.dto.persist;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundlePersistResponse {
    private Long bundleId;
    private Long tenantId;
    private Long turnoId;
    private Integer sequenceNumber;
    private String bundleVersion;
    private String bundleType;
    private String status;
    private LocalDateTime generatedAt;

    private EvidenceBundleIntegrityResponse integridade;
    private EvidenceBundleChainResponse cadeiaCustodia;
    private EvidenceBundleRetentionResponse retencao;
    private EvidenceBundleVerificationResponse verification;
}

