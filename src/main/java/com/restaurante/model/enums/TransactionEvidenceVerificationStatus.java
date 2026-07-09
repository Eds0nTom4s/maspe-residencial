package com.restaurante.model.enums;

public enum TransactionEvidenceVerificationStatus {
    NOT_VERIFIED,
    VALID,
    INVALID_HASH,
    INVALID_SIGNATURE,
    BROKEN_CHAIN,
    SEQUENCE_GAP
}

