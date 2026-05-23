package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;

public class TenantOfflineSyncCommandSummaryResponse {

    private String clientRequestId;
    private DeviceOfflineCommandType commandType;
    private DeviceOfflineCommandStatus status;
    private Integer commandIndex;
    private Integer payloadSizeBytes;
    private String dependsOnClientRequestId;
    private String dependencyStatus;
    private String createdEntityType;
    private Long createdEntityId;
    private String errorCode;
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

    public Integer getCommandIndex() {
        return commandIndex;
    }

    public void setCommandIndex(Integer commandIndex) {
        this.commandIndex = commandIndex;
    }

    public Integer getPayloadSizeBytes() {
        return payloadSizeBytes;
    }

    public void setPayloadSizeBytes(Integer payloadSizeBytes) {
        this.payloadSizeBytes = payloadSizeBytes;
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getConflictCode() {
        return conflictCode;
    }

    public void setConflictCode(String conflictCode) {
        this.conflictCode = conflictCode;
    }
}

