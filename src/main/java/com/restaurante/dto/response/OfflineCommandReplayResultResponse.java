package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DeviceOfflineReplayStatus;

public class OfflineCommandReplayResultResponse {

    private Long commandId;
    private String clientRequestId;
    private DeviceOfflineCommandType commandType;
    private DeviceOfflineCommandStatus previousStatus;
    private DeviceOfflineReplayStatus replayStatus;
    private String eligibilityStatus;
    private String eligibilityReason;
    private String resultStatus;
    private String createdEntityType;
    private Long createdEntityId;
    private String errorCode;
    private String errorMessage;

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

    public DeviceOfflineCommandType getCommandType() {
        return commandType;
    }

    public void setCommandType(DeviceOfflineCommandType commandType) {
        this.commandType = commandType;
    }

    public DeviceOfflineCommandStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(DeviceOfflineCommandStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public DeviceOfflineReplayStatus getReplayStatus() {
        return replayStatus;
    }

    public void setReplayStatus(DeviceOfflineReplayStatus replayStatus) {
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

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
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
}

