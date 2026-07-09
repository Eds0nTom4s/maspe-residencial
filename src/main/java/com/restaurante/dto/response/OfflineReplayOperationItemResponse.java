package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceOfflineReplayOperationItemStatus;

import java.time.Instant;

public class OfflineReplayOperationItemResponse {

    private Long itemId;
    private Long commandId;
    private String clientRequestId;
    private String commandType;
    private String previousStatus;
    private DeviceOfflineReplayOperationItemStatus itemStatus;
    private String eligibilityStatus;
    private String eligibilityReason;
    private Long replayAttemptId;
    private String resultStatus;
    private String errorCode;
    private String errorMessage;
    private int attempts;
    private Instant nextRetryAt;
    private Instant startedAt;
    private Instant finishedAt;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

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

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public DeviceOfflineReplayOperationItemStatus getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(DeviceOfflineReplayOperationItemStatus itemStatus) {
        this.itemStatus = itemStatus;
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

    public Long getReplayAttemptId() {
        return replayAttemptId;
    }

    public void setReplayAttemptId(Long replayAttemptId) {
        this.replayAttemptId = replayAttemptId;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
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
}

