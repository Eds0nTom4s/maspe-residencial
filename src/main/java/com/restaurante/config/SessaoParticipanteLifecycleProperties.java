package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.sessao.participantes")
public class SessaoParticipanteLifecycleProperties {

    private int inviteTtlMinutes = 30;
    private int pendingApprovalTtlMinutes = 60;
    private int resendCooldownSeconds = 60;
    private int maxResends = 3;
    private boolean expirationJobEnabled = true;
    private String expirationJobCron = "0 */1 * * * *";
    private int expirationBatchSize = 200;

    public int getInviteTtlMinutes() {
        return inviteTtlMinutes;
    }

    public void setInviteTtlMinutes(int inviteTtlMinutes) {
        this.inviteTtlMinutes = inviteTtlMinutes;
    }

    public int getPendingApprovalTtlMinutes() {
        return pendingApprovalTtlMinutes;
    }

    public void setPendingApprovalTtlMinutes(int pendingApprovalTtlMinutes) {
        this.pendingApprovalTtlMinutes = pendingApprovalTtlMinutes;
    }

    public int getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public void setResendCooldownSeconds(int resendCooldownSeconds) {
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public int getMaxResends() {
        return maxResends;
    }

    public void setMaxResends(int maxResends) {
        this.maxResends = maxResends;
    }

    public boolean isExpirationJobEnabled() {
        return expirationJobEnabled;
    }

    public void setExpirationJobEnabled(boolean expirationJobEnabled) {
        this.expirationJobEnabled = expirationJobEnabled;
    }

    public String getExpirationJobCron() {
        return expirationJobCron;
    }

    public void setExpirationJobCron(String expirationJobCron) {
        this.expirationJobCron = expirationJobCron;
    }

    public int getExpirationBatchSize() {
        return expirationBatchSize;
    }

    public void setExpirationBatchSize(int expirationBatchSize) {
        this.expirationBatchSize = expirationBatchSize;
    }
}

