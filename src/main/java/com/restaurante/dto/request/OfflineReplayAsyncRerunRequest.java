package com.restaurante.dto.request;

import com.restaurante.model.enums.DeviceOfflineReplayOperationItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class OfflineReplayAsyncRerunRequest {

    @NotEmpty
    private List<DeviceOfflineReplayOperationItemStatus> onlyStatuses;

    @NotBlank
    private String reason;

    private Boolean force = Boolean.FALSE;

    public List<DeviceOfflineReplayOperationItemStatus> getOnlyStatuses() {
        return onlyStatuses;
    }

    public void setOnlyStatuses(List<DeviceOfflineReplayOperationItemStatus> onlyStatuses) {
        this.onlyStatuses = onlyStatuses;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }
}

