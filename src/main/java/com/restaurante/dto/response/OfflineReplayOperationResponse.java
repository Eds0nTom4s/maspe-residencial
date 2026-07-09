package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceOfflineReplayOperationStatus;

import java.time.Instant;

public class OfflineReplayOperationResponse {

    private String operationId;
    private String serverSyncId;
    private DeviceOfflineReplayOperationStatus status;
    private int totalItems;
    private int pendingItems;
    private int runningItems;
    private int succeededItems;
    private int noopItems;
    private int blockedItems;
    private int failedItems;
    private int progressPercent;
    private Instant requestedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant lastProgressAt;
    private String reason;
    private Long requestedBy;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getServerSyncId() {
        return serverSyncId;
    }

    public void setServerSyncId(String serverSyncId) {
        this.serverSyncId = serverSyncId;
    }

    public DeviceOfflineReplayOperationStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceOfflineReplayOperationStatus status) {
        this.status = status;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getPendingItems() {
        return pendingItems;
    }

    public void setPendingItems(int pendingItems) {
        this.pendingItems = pendingItems;
    }

    public int getRunningItems() {
        return runningItems;
    }

    public void setRunningItems(int runningItems) {
        this.runningItems = runningItems;
    }

    public int getSucceededItems() {
        return succeededItems;
    }

    public void setSucceededItems(int succeededItems) {
        this.succeededItems = succeededItems;
    }

    public int getNoopItems() {
        return noopItems;
    }

    public void setNoopItems(int noopItems) {
        this.noopItems = noopItems;
    }

    public int getBlockedItems() {
        return blockedItems;
    }

    public void setBlockedItems(int blockedItems) {
        this.blockedItems = blockedItems;
    }

    public int getFailedItems() {
        return failedItems;
    }

    public void setFailedItems(int failedItems) {
        this.failedItems = failedItems;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getLastProgressAt() {
        return lastProgressAt;
    }

    public void setLastProgressAt(Instant lastProgressAt) {
        this.lastProgressAt = lastProgressAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }
}

