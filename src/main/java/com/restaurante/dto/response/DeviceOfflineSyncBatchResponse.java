package com.restaurante.dto.response;

import java.time.Instant;
import java.util.List;

public class DeviceOfflineSyncBatchResponse {

    private String serverSyncId;
    private String syncSessionId;
    private String syncSessionStatus;
    private String syncId;
    private Instant receivedAt;
    private Instant startedProcessingAt;
    private Instant finishedProcessingAt;
    private Long durationMs;
    private Integer totalPayloadBytes;
    private int total;
    private int applied;
    private int duplicates;
    private int rejected;
    private int conflicts;
    private int failed;
    private List<DeviceOfflineCommandResultResponse> results;

    public String getServerSyncId() {
        return serverSyncId;
    }

    public void setServerSyncId(String serverSyncId) {
        this.serverSyncId = serverSyncId;
    }

    public String getSyncSessionId() {
        return syncSessionId;
    }

    public void setSyncSessionId(String syncSessionId) {
        this.syncSessionId = syncSessionId;
    }

    public String getSyncSessionStatus() {
        return syncSessionStatus;
    }

    public void setSyncSessionStatus(String syncSessionStatus) {
        this.syncSessionStatus = syncSessionStatus;
    }

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

    public Instant getStartedProcessingAt() {
        return startedProcessingAt;
    }

    public void setStartedProcessingAt(Instant startedProcessingAt) {
        this.startedProcessingAt = startedProcessingAt;
    }

    public Instant getFinishedProcessingAt() {
        return finishedProcessingAt;
    }

    public void setFinishedProcessingAt(Instant finishedProcessingAt) {
        this.finishedProcessingAt = finishedProcessingAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getTotalPayloadBytes() {
        return totalPayloadBytes;
    }

    public void setTotalPayloadBytes(Integer totalPayloadBytes) {
        this.totalPayloadBytes = totalPayloadBytes;
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
