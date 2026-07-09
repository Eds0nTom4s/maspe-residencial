package com.restaurante.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public class DeviceOfflineSyncBatchRequest {

    private String syncSessionId;
    private Instant deviceLocalTime;
    private String appVersion;
    private Instant offlineStartedAt;
    private Instant offlineEndedAt;

    @NotEmpty
    private List<DeviceOfflineCommandRequest> commands;

    public String getSyncSessionId() {
        return syncSessionId;
    }

    public void setSyncSessionId(String syncSessionId) {
        this.syncSessionId = syncSessionId;
    }

    public Instant getDeviceLocalTime() {
        return deviceLocalTime;
    }

    public void setDeviceLocalTime(Instant deviceLocalTime) {
        this.deviceLocalTime = deviceLocalTime;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public Instant getOfflineStartedAt() {
        return offlineStartedAt;
    }

    public void setOfflineStartedAt(Instant offlineStartedAt) {
        this.offlineStartedAt = offlineStartedAt;
    }

    public Instant getOfflineEndedAt() {
        return offlineEndedAt;
    }

    public void setOfflineEndedAt(Instant offlineEndedAt) {
        this.offlineEndedAt = offlineEndedAt;
    }

    public List<DeviceOfflineCommandRequest> getCommands() {
        return commands;
    }

    public void setCommands(List<DeviceOfflineCommandRequest> commands) {
        this.commands = commands;
    }
}

