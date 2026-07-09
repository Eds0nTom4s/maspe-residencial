package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.device.offline-sync.observability")
public class DeviceOfflineSyncObservabilityProperties {

    private boolean enabled = true;
    private int maxQueryDays = 31;
    private int defaultPageSize = 20;
    private int maxPageSize = 100;
    private boolean auditDetailView = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxQueryDays() {
        return maxQueryDays;
    }

    public void setMaxQueryDays(int maxQueryDays) {
        this.maxQueryDays = maxQueryDays;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public boolean isAuditDetailView() {
        return auditDetailView;
    }

    public void setAuditDetailView(boolean auditDetailView) {
        this.auditDetailView = auditDetailView;
    }
}

