package com.restaurante.model.enums;

public enum FiscalAutoIssueJobStatus {
    PENDING,
    PROCESSING,
    ISSUED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    CANCELLED,
    SKIPPED
}

