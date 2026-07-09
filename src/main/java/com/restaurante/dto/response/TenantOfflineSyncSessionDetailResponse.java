package com.restaurante.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class TenantOfflineSyncSessionDetailResponse {

    private String serverSyncId;
    private String syncSessionId;
    private String status;
    private String appVersion;
    private Long unidadeId;
    private String unidadeNome;
    private Long deviceId;
    private String deviceName;
    private Instant receivedAt;
    private Instant startedProcessingAt;
    private Instant finishedProcessingAt;
    private Long durationMs;
    private int totalCommands;
    private int appliedCount;
    private int duplicateCount;
    private int rejectedCount;
    private int conflictCount;
    private int failedCount;
    private Integer totalPayloadBytes;
    private Integer maxCommandPayloadBytes;
    private int localRefCount;
    private JsonNode summary;
    private JsonNode errorSummary;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public Long getUnidadeId() {
        return unidadeId;
    }

    public void setUnidadeId(Long unidadeId) {
        this.unidadeId = unidadeId;
    }

    public String getUnidadeNome() {
        return unidadeNome;
    }

    public void setUnidadeNome(String unidadeNome) {
        this.unidadeNome = unidadeNome;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
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

    public int getTotalCommands() {
        return totalCommands;
    }

    public void setTotalCommands(int totalCommands) {
        this.totalCommands = totalCommands;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public void setAppliedCount(int appliedCount) {
        this.appliedCount = appliedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(int rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getTotalPayloadBytes() {
        return totalPayloadBytes;
    }

    public void setTotalPayloadBytes(Integer totalPayloadBytes) {
        this.totalPayloadBytes = totalPayloadBytes;
    }

    public Integer getMaxCommandPayloadBytes() {
        return maxCommandPayloadBytes;
    }

    public void setMaxCommandPayloadBytes(Integer maxCommandPayloadBytes) {
        this.maxCommandPayloadBytes = maxCommandPayloadBytes;
    }

    public int getLocalRefCount() {
        return localRefCount;
    }

    public void setLocalRefCount(int localRefCount) {
        this.localRefCount = localRefCount;
    }

    public JsonNode getSummary() {
        return summary;
    }

    public void setSummary(JsonNode summary) {
        this.summary = summary;
    }

    public JsonNode getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(JsonNode errorSummary) {
        this.errorSummary = errorSummary;
    }
}

