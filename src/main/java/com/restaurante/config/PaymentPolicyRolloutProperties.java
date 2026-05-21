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
}

