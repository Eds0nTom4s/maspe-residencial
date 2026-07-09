package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceOfflineReplayEligibilityReason;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityStatus;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;

import java.util.List;

public class OfflineCommandReplayEligibilityResponse {

    private Long commandId;
    private String clientRequestId;
    private DeviceOfflineCommandType commandType;
    private DeviceOfflineCommandStatus currentStatus;
    private DeviceOfflineReplayEligibilityStatus eligibilityStatus;
    private DeviceOfflineReplayEligibilityReason reason;
    private boolean eligible;
    private String recommendedAction;
    private List<String> warnings;
    private String createdEntityType;
    private Long createdEntityId;

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

    public DeviceOfflineCommandStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(DeviceOfflineCommandStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public DeviceOfflineReplayEligibilityStatus getEligibilityStatus() {
        return eligibilityStatus;
    }

    public void setEligibilityStatus(DeviceOfflineReplayEligibilityStatus eligibilityStatus) {
        this.eligibilityStatus = eligibilityStatus;
    }

    public DeviceOfflineReplayEligibilityReason getReason() {
        return reason;
    }

    public void setReason(DeviceOfflineReplayEligibilityReason reason) {
        this.reason = reason;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
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
}

