package com.restaurante.financeiro.snapshot.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SnapshotIntegridadeResponse {
    private String hashAlgorithm;
    private String snapshotHash;
    private String canonicalizationVersion;
    private LocalDateTime hashGeneratedAt;
    private String hashScope;

    private String signatureAlgorithm;
    private String snapshotSignature;
    private LocalDateTime signatureGeneratedAt;
    private String signatureKeyId;
    private String signatureScope;
}
