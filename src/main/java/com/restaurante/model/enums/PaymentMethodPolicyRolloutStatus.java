package com.restaurante.model.enums;

public enum PaymentMethodPolicyRolloutStatus {
    PREVIEW,
    PENDING,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_SKIPS,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED,
    // legado do Prompt 38.3 (execução sync)
    APPLIED
}
