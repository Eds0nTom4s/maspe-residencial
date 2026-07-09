package com.restaurante.financeiro.snapshot;

public enum SnapshotSignatureFailureReason {
    KEY_ID_MISSING,
    KEY_NOT_FOUND,
    KEY_DISABLED,
    SIGNATURE_MISSING,
    SIGNATURE_MISMATCH,
    SECRET_UNAVAILABLE,
    ALGORITHM_UNSUPPORTED
}

