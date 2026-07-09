package com.restaurante.model.enums;

public enum CallbackProcessingStatus {
    RECEIVED,
    PROCESSED,
    IGNORED_DUPLICATE,
    FAILED,
    INVALID_SIGNATURE,
    PAYMENT_NOT_FOUND
}

