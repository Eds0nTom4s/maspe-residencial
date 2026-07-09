package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.device.offline-replay.async")
public class DeviceOfflineReplayAsyncProperties {

    private boolean enabled = true;
    private boolean workerEnabled = true;
    private String workerCron = "*/30 * * * * *";
    private int batchSize = 50;
    private int maxItemsPerOperation = 500;
    private int maxActiveOperationsPerTenant = 3;
    private int maxOperationsPerTenantPerHour = 20;
    private int maxAttemptsPerItem = 3;
    private int initialBackoffSeconds = 30;
    private int maxBackoffSeconds = 900;
    private int lockTimeoutSeconds = 300;
    private int progressEventMinPercentDelta = 10;
    private int progressEventMinIntervalSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public String getWorkerCron() {
        return workerCron;
    }

    public void setWorkerCron(String workerCron) {
        this.workerCron = workerCron;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxItemsPerOperation() {
        return maxItemsPerOperation;
    }

    public void setMaxItemsPerOperation(int maxItemsPerOperation) {
        this.maxItemsPerOperation = maxItemsPerOperation;
    }

    public int getMaxActiveOperationsPerTenant() {
        return maxActiveOperationsPerTenant;
    }

    public void setMaxActiveOperationsPerTenant(int maxActiveOperationsPerTenant) {
        this.maxActiveOperationsPerTenant = maxActiveOperationsPerTenant;
    }

    public int getMaxOperationsPerTenantPerHour() {
        return maxOperationsPerTenantPerHour;
    }

    public void setMaxOperationsPerTenantPerHour(int maxOperationsPerTenantPerHour) {
        this.maxOperationsPerTenantPerHour = maxOperationsPerTenantPerHour;
    }

    public int getMaxAttemptsPerItem() {
        return maxAttemptsPerItem;
    }

    public void setMaxAttemptsPerItem(int maxAttemptsPerItem) {
        this.maxAttemptsPerItem = maxAttemptsPerItem;
    }

    public int getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public void setInitialBackoffSeconds(int initialBackoffSeconds) {
        this.initialBackoffSeconds = initialBackoffSeconds;
    }

    public int getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public void setMaxBackoffSeconds(int maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    public int getLockTimeoutSeconds() {
        return lockTimeoutSeconds;
    }

    public void setLockTimeoutSeconds(int lockTimeoutSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    public int getProgressEventMinPercentDelta() {
        return progressEventMinPercentDelta;
    }

    public void setProgressEventMinPercentDelta(int progressEventMinPercentDelta) {
        this.progressEventMinPercentDelta = progressEventMinPercentDelta;
    }

    public int getProgressEventMinIntervalSeconds() {
        return progressEventMinIntervalSeconds;
    }

    public void setProgressEventMinIntervalSeconds(int progressEventMinIntervalSeconds) {
        this.progressEventMinIntervalSeconds = progressEventMinIntervalSeconds;
    }
}

