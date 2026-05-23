package com.restaurante.dto.response;

import com.restaurante.model.enums.DeviceOfflineCommandType;

import java.time.Instant;
import java.util.Set;

public class DeviceOfflineCapabilitiesResponse {

    private boolean offlineEnabled;
    private Set<DeviceOfflineCommandType> allowedCommandTypes;
    private int maxBatchSize;
    private int maxOfflineAgeMinutes;
    private Instant serverTime;
    private Long deviceId;
    private Long unidadeId;
    private Long tenantId;

    public boolean isOfflineEnabled() {
        return offlineEnabled;
    }

    public void setOfflineEnabled(boolean offlineEnabled) {
        this.offlineEnabled = offlineEnabled;
    }

    public Set<DeviceOfflineCommandType> getAllowedCommandTypes() {
        return allowedCommandTypes;
    }

    public void setAllowedCommandTypes(Set<DeviceOfflineCommandType> allowedCommandTypes) {
        this.allowedCommandTypes = allowedCommandTypes;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxOfflineAgeMinutes() {
        return maxOfflineAgeMinutes;
    }

    public void setMaxOfflineAgeMinutes(int maxOfflineAgeMinutes) {
        this.maxOfflineAgeMinutes = maxOfflineAgeMinutes;
    }

    public Instant getServerTime() {
        return serverTime;
    }

    public void setServerTime(Instant serverTime) {
        this.serverTime = serverTime;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getUnidadeId() {
        return unidadeId;
    }

    public void setUnidadeId(Long unidadeId) {
        this.unidadeId = unidadeId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}

