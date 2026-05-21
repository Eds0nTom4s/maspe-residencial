package com.restaurante.financeiro.snapshot.evidence.dto.persist;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleRetentionResponse {
    private String retentionPolicy;
    private LocalDateTime retentionUntil;
    private boolean wormLocked;
    private boolean deleteAllowed;
    private String deleteReason;
}

