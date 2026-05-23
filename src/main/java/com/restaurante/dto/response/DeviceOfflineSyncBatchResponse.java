package com.restaurante.dto.response;

import java.time.Instant;
import java.util.List;

public class DeviceOfflineSyncBatchResponse {

    private String syncId;
    private Instant receivedAt;
    private int total;
    private int applied;
    private int duplicates;
    private int rejected;
    private int conflicts;
    private int failed;
    private List<DeviceOfflineCommandResultResponse> results;

    public String getSyncId() {
        return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getApplied() {
        return applied;
    }

    public void setApplied(int applied) {
        this.applied = applied;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public int getConflicts() {
        return conflicts;
    }

    public void setConflicts(int conflicts) {
        this.conflicts = conflicts;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<DeviceOfflineCommandResultResponse> getResults() {
        return results;
    }

    public void setResults(List<DeviceOfflineCommandResultResponse> results) {
        this.results = results;
    }
}

