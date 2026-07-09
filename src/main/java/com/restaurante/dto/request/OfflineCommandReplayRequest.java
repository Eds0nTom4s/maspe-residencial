package com.restaurante.dto.request;

import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class OfflineCommandReplayRequest {

    private List<Long> commandIds;

    @NotEmpty
    private List<DeviceOfflineCommandStatus> statuses;

    private List<DeviceOfflineCommandType> commandTypes;

    @NotBlank
    private String reason;

    private Boolean dryRun = Boolean.FALSE;
    private Boolean force = Boolean.FALSE;

    public List<Long> getCommandIds() {
        return commandIds;
    }

    public void setCommandIds(List<Long> commandIds) {
        this.commandIds = commandIds;
    }

    public List<DeviceOfflineCommandStatus> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<DeviceOfflineCommandStatus> statuses) {
        this.statuses = statuses;
    }

    public List<DeviceOfflineCommandType> getCommandTypes() {
        return commandTypes;
    }

    public void setCommandTypes(List<DeviceOfflineCommandType> commandTypes) {
        this.commandTypes = commandTypes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }
}

