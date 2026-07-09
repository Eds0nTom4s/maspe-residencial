package com.restaurante.financeiro.snapshot;

public class SnapshotSignatureKeyProperties {
    private String secret;
    private SnapshotSignatureKeyStatus status = SnapshotSignatureKeyStatus.DISABLED;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public SnapshotSignatureKeyStatus getStatus() {
        return status;
    }

    public void setStatus(SnapshotSignatureKeyStatus status) {
        this.status = status;
    }
}

