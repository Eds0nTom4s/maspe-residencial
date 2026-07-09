package com.restaurante.financeiro.snapshot.evidence.dto.persist;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleChainResponse {
    private Integer sequenceNumber;
    private Long previousBundleId;
    private String previousBundleHash;
    private String chainHash;
    private String chainSignature;
    private String chainSignatureKeyId;
    private String chainSignatureKeyStatus;
    private LocalDateTime chainSignatureGeneratedAt;
}

