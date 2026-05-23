package com.restaurante.dto.request;

import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class OfflineCommandReplayPreviewRequest {

    @NotEmpty
    private List<DeviceOfflineCommandStatus> statuses;
    private List<DeviceOfflineCommandType> commandTypes;
    private Boolean onlyEligible = Boolean.TRUE;
    private Boolean includeWarnings = Boolean.TRUE;

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

    public Boolean getOnlyEligible() {
        return onlyEligible;
    }

    public void setOnlyEligible(Boolean onlyEligible) {
        this.onlyEligible = onlyEligible;
    }

    public Boolean getIncludeWarnings() {
        return includeWarnings;
    }

    public void setIncludeWarnings(Boolean includeWarnings) {
        this.includeWarnings = includeWarnings;
    }
}

