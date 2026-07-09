package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class TransactionLedgerEvidenceSectionDTO {
    private LocalDateTime generatedAt;
    private Long tenantId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private Integer totalLedgerEvents;
    private Long firstSequence;
    private Long lastSequence;
    private String firstEventHash;
    private String lastEventHash;
    private String lastLedgerStateHash;

    private String verificationStatus;
    private Long latestVerificationRunId;
    private Integer invalidEventsCount;
    private Integer brokenChainCount;
    private Integer sequenceGapCount;

    private Map<String, Integer> byEventType;
    private Map<String, Integer> bySourceModule;
    private List<String> warnings;
}

