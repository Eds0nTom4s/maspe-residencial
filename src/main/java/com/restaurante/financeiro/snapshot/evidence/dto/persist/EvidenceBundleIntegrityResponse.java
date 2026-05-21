package com.restaurante.financeiro.snapshot.evidence.dto.persist;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleIntegrityResponse {
    private String canonicalizationVersion;
    private String hashAlgorithm;
    private String bundleHash;
    private String signatureAlgorithm;
    private String bundleSignature;
    private String signatureKeyId;
    private String signatureKeyStatus;
    private LocalDateTime signatureGeneratedAt;
}

