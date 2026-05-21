package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.financeiro.payment-policy-rollout")
public class PaymentPolicyRolloutProperties {

    private boolean asyncEnabled = true;
    private boolean workerEnabled = true;
    private long fixedDelayMs = 30_000L;
    private int batchSize = 100;
    private int maxAttempts = 3;
    private int lockTimeoutSeconds = 300;

    private boolean staleRecoveryEnabled = true;
    private int staleLockTimeoutSeconds = 300;
    private int staleItemTimeoutSeconds = 300;

    private boolean itemBackoffEnabled = true;
    private int initialBackoffSeconds = 30;
    private int maxBackoffSeconds = 900;
    private double backoffMultiplier = 2.0d;

    private boolean progressEventEnabled = true;
    private int progressEventMinPercentDelta = 10;
    private int progressEventMinIntervalSeconds = 60;

    private boolean allowRerunCancelled = false;

    public boolean isAsyncEnabled() { return asyncEnabled; }
    public void setAsyncEnabled(boolean asyncEnabled) { this.asyncEnabled = asyncEnabled; }
    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public long getFixedDelayMs() { return fixedDelayMs; }
    public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getLockTimeoutSeconds() { return lockTimeoutSeconds; }
    public void setLockTimeoutSeconds(int lockTimeoutSeconds) { this.lockTimeoutSeconds = lockTimeoutSeconds; }

    public boolean isStaleRecoveryEnabled() { return staleRecoveryEnabled; }
    public void setStaleRecoveryEnabled(boolean staleRecoveryEnabled) { this.staleRecoveryEnabled = staleRecoveryEnabled; }
    public int getStaleLockTimeoutSeconds() { return staleLockTimeoutSeconds; }
    public void setStaleLockTimeoutSeconds(int staleLockTimeoutSeconds) { this.staleLockTimeoutSeconds = staleLockTimeoutSeconds; }
    public int getStaleItemTimeoutSeconds() { return staleItemTimeoutSeconds; }
    public void setStaleItemTimeoutSeconds(int staleItemTimeoutSeconds) { this.staleItemTimeoutSeconds = staleItemTimeoutSeconds; }

    public boolean isItemBackoffEnabled() { return itemBackoffEnabled; }
    public void setItemBackoffEnabled(boolean itemBackoffEnabled) { this.itemBackoffEnabled = itemBackoffEnabled; }
    public int getInitialBackoffSeconds() { return initialBackoffSeconds; }
    public void setInitialBackoffSeconds(int initialBackoffSeconds) { this.initialBackoffSeconds = initialBackoffSeconds; }
    public int getMaxBackoffSeconds() { return maxBackoffSeconds; }
    public void setMaxBackoffSeconds(int maxBackoffSeconds) { this.maxBackoffSeconds = maxBackoffSeconds; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }

    public boolean isProgressEventEnabled() { return progressEventEnabled; }
    public void setProgressEventEnabled(boolean progressEventEnabled) { this.progressEventEnabled = progressEventEnabled; }
    public int getProgressEventMinPercentDelta() { return progressEventMinPercentDelta; }
    public void setProgressEventMinPercentDelta(int progressEventMinPercentDelta) { this.progressEventMinPercentDelta = progressEventMinPercentDelta; }
    public int getProgressEventMinIntervalSeconds() { return progressEventMinIntervalSeconds; }
    public void setProgressEventMinIntervalSeconds(int progressEventMinIntervalSeconds) { this.progressEventMinIntervalSeconds = progressEventMinIntervalSeconds; }

    public boolean isAllowRerunCancelled() { return allowRerunCancelled; }
    public void setAllowRerunCancelled(boolean allowRerunCancelled) { this.allowRerunCancelled = allowRerunCancelled; }
}
