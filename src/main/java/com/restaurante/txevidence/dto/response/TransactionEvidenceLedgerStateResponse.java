package com.restaurante.txevidence.dto.response;

import com.restaurante.model.enums.TransactionEvidenceLedgerStateStatus;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class TransactionEvidenceLedgerStateResponse {
    Long id;
    Long lastSequence;
    String lastEventHash;
    Long lastEventId;
    LocalDateTime lastRecordedAt;
    TransactionEvidenceLedgerStateStatus status;
}

