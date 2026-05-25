package com.restaurante.model.enums;

public enum TransactionEvidenceVerificationIssueType {
    HASH_MISMATCH,
    SIGNATURE_MISMATCH,
    BROKEN_CHAIN,
    SEQUENCE_GAP,
    MISSING_EVENT,
    DUPLICATE_SEQUENCE,
    DUPLICATE_IDEMPOTENCY_KEY
}

