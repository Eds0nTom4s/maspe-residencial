package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.payment.polling")
public class PaymentPollingProperties {

    private boolean enabled = true;
    private long fixedDelayMs = 60_000L;
    private int batchSize = 50;
    private int maxAttempts = 10;
    private int initialDelayMinutes = 2;
    private int maxAgeHours = 24;
    private int backoffMultiplier = 2;
    private int maxBackoffMinutes = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getFixedDelayMs() { return fixedDelayMs; }
    public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getInitialDelayMinutes() { return initialDelayMinutes; }
    public void setInitialDelayMinutes(int initialDelayMinutes) { this.initialDelayMinutes = initialDelayMinutes; }
    public int getMaxAgeHours() { return maxAgeHours; }
    public void setMaxAgeHours(int maxAgeHours) { this.maxAgeHours = maxAgeHours; }
    public int getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(int backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    public int getMaxBackoffMinutes() { return maxBackoffMinutes; }
    public void setMaxBackoffMinutes(int maxBackoffMinutes) { this.maxBackoffMinutes = maxBackoffMinutes; }
}

