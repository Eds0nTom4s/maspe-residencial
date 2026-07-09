package com.restaurante.financeiro.snapshot.evidence.dto.persist;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleListItemResponse {
    private Long bundleId;
    private Integer sequenceNumber;
    private String bundleVersion;
    private String bundleType;
    private String status;
    private LocalDateTime generatedAt;
    private Long generatedByUserId;

    private String bundleHash;
    private String signatureKeyId;
    private String chainHash;
    private LocalDateTime retentionUntil;
    private boolean wormLocked;
}

