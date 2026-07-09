package com.restaurante.financeiro.snapshot.dto;

import com.restaurante.financeiro.snapshot.SnapshotSignatureFailureReason;
import com.restaurante.financeiro.snapshot.SnapshotSignatureKeyStatus;
import lombok.Data;

@Data
public class SnapshotSignatureVerificationResult {
    private boolean signatureValid;
    private boolean keyFound;
    private SnapshotSignatureKeyStatus keyStatus;
    private String keyId;
    private String algorithm;
    private SnapshotSignatureFailureReason failureReason;
    private String failureReasonDetail;
}

