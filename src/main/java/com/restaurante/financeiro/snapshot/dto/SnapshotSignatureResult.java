package com.restaurante.financeiro.snapshot.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SnapshotSignatureResult {
    private String signature;
    private String keyId;
    private String algorithm;
    private LocalDateTime generatedAt;
}

