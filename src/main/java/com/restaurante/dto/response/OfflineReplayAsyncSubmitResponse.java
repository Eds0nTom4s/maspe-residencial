package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceOfflineReplayOperationStatus;

import java.time.Instant;

public class OfflineReplayAsyncSubmitResponse {

    private String operationId;
    private String serverSyncId;
    private DeviceOfflineReplayOperationStatus status;
    private int totalItems;
    private Instant requestedAt;
    private int progressPercent;

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

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }
}

