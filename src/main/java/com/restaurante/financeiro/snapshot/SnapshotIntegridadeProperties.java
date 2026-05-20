package com.restaurante.financeiro.snapshot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.financeiro.snapshot-integridade")
public class SnapshotIntegridadeProperties {

    private boolean enabled = true;
    private String algorithm = "SHA-256";
    private String canonicalizationVersion = "1.0";
    private boolean auditExport = true;

    private boolean signatureEnabled = true;
    private String signatureAlgorithm = "HMAC-SHA256";
    private String signatureKeyId = "platform-snapshot-key-v1";
    private String signatureSecret;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCanonicalizationVersion() {
        return canonicalizationVersion;
    }

    public void setCanonicalizationVersion(String canonicalizationVersion) {
        this.canonicalizationVersion = canonicalizationVersion;
    }

    public boolean isAuditExport() {
        return auditExport;
    }

    public void setAuditExport(boolean auditExport) {
        this.auditExport = auditExport;
    }

    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }

    public void setSignatureEnabled(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getSignatureKeyId() {
        return signatureKeyId;
    }

    public void setSignatureKeyId(String signatureKeyId) {
        this.signatureKeyId = signatureKeyId;
    }

    public String getSignatureSecret() {
        return signatureSecret;
    }

    public void setSignatureSecret(String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }
}
