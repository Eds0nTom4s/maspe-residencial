package com.restaurante.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;

public class DeviceOfflineCommandResultResponse {

    private String clientRequestId;
    private DeviceOfflineCommandType commandType;
    private DeviceOfflineCommandStatus status;
    private String createdEntityType;
    private Long createdEntityId;
    private JsonNode result;
    private String errorCode;
    private String errorMessage;
    private String conflictCode;

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

    public DeviceOfflineCommandStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceOfflineCommandStatus status) {
        this.status = status;
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

    public JsonNode getResult() {
        return result;
    }

    public void setResult(JsonNode result) {
        this.result = result;
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

    public String getConflictCode() {
        return conflictCode;
    }

    public void setConflictCode(String conflictCode) {
        this.conflictCode = conflictCode;
    }
}

