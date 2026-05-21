package com.restaurante.financeiro.snapshot.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.financeiro.evidence-bundle")
public class EvidenceBundleProperties {

    private boolean retentionEnabled = true;
    private int defaultRetentionDays = 1825;
    private boolean wormLockEnabled = true;
    private boolean allowPhysicalDelete = false;
    private int maxEvents = 100;

    private String canonicalizationVersion = "1.0";
    private String hashAlgorithm = "SHA-256";

    public boolean isRetentionEnabled() {
        return retentionEnabled;
    }

    public void setRetentionEnabled(boolean retentionEnabled) {
        this.retentionEnabled = retentionEnabled;
    }

    public int getDefaultRetentionDays() {
        return defaultRetentionDays;
    }

    public void setDefaultRetentionDays(int defaultRetentionDays) {
        this.defaultRetentionDays = defaultRetentionDays;
    }

    public boolean isWormLockEnabled() {
        return wormLockEnabled;
    }

    public void setWormLockEnabled(boolean wormLockEnabled) {
        this.wormLockEnabled = wormLockEnabled;
    }

    public boolean isAllowPhysicalDelete() {
        return allowPhysicalDelete;
    }

    public void setAllowPhysicalDelete(boolean allowPhysicalDelete) {
        this.allowPhysicalDelete = allowPhysicalDelete;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    public String getCanonicalizationVersion() {
        return canonicalizationVersion;
    }

    public void setCanonicalizationVersion(String canonicalizationVersion) {
        this.canonicalizationVersion = canonicalizationVersion;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }
}

