package com.restaurante.dto.response;

import java.time.Instant;
import java.util.List;

public class OfflineCommandReplayBatchResponse {

    private String operationId;
    private String serverSyncId;
    private Instant requestedAt;
    private int requested;
    private int succeeded;
    private int noop;
    private int blocked;
    private int failed;
    private List<OfflineCommandReplayResultResponse> results;

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

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public int getRequested() {
        return requested;
    }

    public void setRequested(int requested) {
        this.requested = requested;
    }

    public int getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }

    public int getNoop() {
        return noop;
    }

    public void setNoop(int noop) {
        this.noop = noop;
    }

    public int getBlocked() {
        return blocked;
    }

    public void setBlocked(int blocked) {
        this.blocked = blocked;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<OfflineCommandReplayResultResponse> getResults() {
        return results;
    }

    public void setResults(List<OfflineCommandReplayResultResponse> results) {
        this.results = results;
    }
}

