package com.restaurante.model.enums;

public enum DeviceOfflineReplayOperationStatus {
    // TODO Prompt 40.5: cancelamento controlado de replay operations (CANCEL_REQUESTED + CANCELLED final)
    PENDING,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_NOOPS,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED
}
