package com.restaurante.dto.response;

import java.time.Instant;
import java.util.List;

public class OfflineSyncDiagnosticExportResponse {

    public static class SessionInfo {
        private String serverSyncId;
        private String syncSessionId;
        private String status;
        private Long unidadeId;
        private Long deviceId;
        private String appVersion;
        private Instant receivedAt;
        private Instant finishedAt;
        private Long durationMs;
        private int totalCommands;
        private int applied;
        private int duplicates;
        private int rejected;
        private int conflicts;
        private int failed;

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

        public Long getUnidadeId() {
            return unidadeId;
        }

        public void setUnidadeId(Long unidadeId) {
            this.unidadeId = unidadeId;
        }

        public Long getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(Long deviceId) {
            this.deviceId = deviceId;
        }

        public String getAppVersion() {
            return appVersion;
        }

        public void setAppVersion(String appVersion) {
            this.appVersion = appVersion;
        }

        public Instant getReceivedAt() {
            return receivedAt;
        }

        public void setReceivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
        }

        public Instant getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
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
    }

    public static class CommandInfo {
        private Long commandId;
        private String clientRequestId;
        private String commandType;
        private String status;
        private String conflictCode;
        private String errorCode;
        private String payloadHash;
        private Integer payloadSizeBytes;
        private Integer commandIndex;
        private String dependsOnClientRequestId;
        private String dependencyStatus;
        private String createdEntityType;
        private Long createdEntityId;
        private int replayCount;
        private Instant lastReplayAttemptAt;

        public Long getCommandId() {
            return commandId;
        }

        public void setCommandId(Long commandId) {
            this.commandId = commandId;
        }

        public String getClientRequestId() {
            return clientRequestId;
        }

        public void setClientRequestId(String clientRequestId) {
            this.clientRequestId = clientRequestId;
        }

        public String getCommandType() {
            return commandType;
        }

        public void setCommandType(String commandType) {
            this.commandType = commandType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getConflictCode() {
            return conflictCode;
        }

        public void setConflictCode(String conflictCode) {
            this.conflictCode = conflictCode;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getPayloadHash() {
            return payloadHash;
        }

        public void setPayloadHash(String payloadHash) {
            this.payloadHash = payloadHash;
        }

        public Integer getPayloadSizeBytes() {
            return payloadSizeBytes;
        }

        public void setPayloadSizeBytes(Integer payloadSizeBytes) {
            this.payloadSizeBytes = payloadSizeBytes;
        }

        public Integer getCommandIndex() {
            return commandIndex;
        }

        public void setCommandIndex(Integer commandIndex) {
            this.commandIndex = commandIndex;
        }

        public String getDependsOnClientRequestId() {
            return dependsOnClientRequestId;
        }

        public void setDependsOnClientRequestId(String dependsOnClientRequestId) {
            this.dependsOnClientRequestId = dependsOnClientRequestId;
        }

        public String getDependencyStatus() {
            return dependencyStatus;
        }

        public void setDependencyStatus(String dependencyStatus) {
            this.dependencyStatus = dependencyStatus;
        }

        public String getCreatedEntityType() {
            return createdEntityType;
        }

        public void setCreatedEntityType(String createdEntityType) {
            this.createdEntityType = createdEntityType;
        }

        public Long getCreatedEntityId() {
            return createdEntityId;
        }

        public void setCreatedEntityId(Long createdEntityId) {
            this.createdEntityId = createdEntityId;
        }

        public int getReplayCount() {
            return replayCount;
        }

        public void setReplayCount(int replayCount) {
            this.replayCount = replayCount;
        }

        public Instant getLastReplayAttemptAt() {
            return lastReplayAttemptAt;
        }

        public void setLastReplayAttemptAt(Instant lastReplayAttemptAt) {
            this.lastReplayAttemptAt = lastReplayAttemptAt;
        }
    }

    public static class ReplayAttemptInfo {
        private Long attemptId;
        private Long commandId;
        private String previousStatus;
        private String replayStatus;
        private String eligibilityStatus;
        private String eligibilityReason;
        private Instant requestedAt;
        private Long requestedBy;
        private String resultStatus;
        private String errorCode;

        public Long getAttemptId() {
            return attemptId;
        }

        public void setAttemptId(Long attemptId) {
            this.attemptId = attemptId;
        }

        public Long getCommandId() {
            return commandId;
        }

        public void setCommandId(Long commandId) {
            this.commandId = commandId;
        }

        public String getPreviousStatus() {
            return previousStatus;
        }

        public void setPreviousStatus(String previousStatus) {
            this.previousStatus = previousStatus;
        }

        public String getReplayStatus() {
            return replayStatus;
        }

        public void setReplayStatus(String replayStatus) {
            this.replayStatus = replayStatus;
        }

        public String getEligibilityStatus() {
            return eligibilityStatus;
        }

        public void setEligibilityStatus(String eligibilityStatus) {
            this.eligibilityStatus = eligibilityStatus;
        }

        public String getEligibilityReason() {
            return eligibilityReason;
        }

        public void setEligibilityReason(String eligibilityReason) {
            this.eligibilityReason = eligibilityReason;
        }

        public Instant getRequestedAt() {
            return requestedAt;
        }

        public void setRequestedAt(Instant requestedAt) {
            this.requestedAt = requestedAt;
        }

        public Long getRequestedBy() {
            return requestedBy;
        }

        public void setRequestedBy(Long requestedBy) {
            this.requestedBy = requestedBy;
        }

        public String getResultStatus() {
            return resultStatus;
        }

        public void setResultStatus(String resultStatus) {
            this.resultStatus = resultStatus;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }
    }

    private SessionInfo session;
    private List<CommandInfo> commands;
    private List<ReplayAttemptInfo> replayAttempts;

    public SessionInfo getSession() {
        return session;
    }

    public void setSession(SessionInfo session) {
        this.session = session;
    }

    public List<CommandInfo> getCommands() {
        return commands;
    }

    public void setCommands(List<CommandInfo> commands) {
        this.commands = commands;
    }

    public List<ReplayAttemptInfo> getReplayAttempts() {
        return replayAttempts;
    }

    public void setReplayAttempts(List<ReplayAttemptInfo> replayAttempts) {
        this.replayAttempts = replayAttempts;
    }
}

