package com.restaurante.dto.response;

import java.util.List;
import java.util.Map;

public class TenantOfflineSyncMetricsResponse {

    private long totalSessions;
    private long totalCommands;
    private long appliedCommands;
    private long duplicateCommands;
    private long rejectedCommands;
    private long conflictCommands;
    private long failedCommands;
    private Long averageDurationMs;
    private Map<String, Long> topConflictCodes;
    private Map<String, Long> topErrorCodes;
    private Map<String, Long> appVersionBreakdown;
    private List<DeviceFailureRankItem> deviceFailureRanking;

    public static class DeviceFailureRankItem {
        private Long deviceId;
        private String deviceName;
        private Long failedCount;

        public Long getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(Long deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public Long getFailedCount() {
            return failedCount;
        }

        public void setFailedCount(Long failedCount) {
            this.failedCount = failedCount;
        }
    }

    public long getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(long totalSessions) {
        this.totalSessions = totalSessions;
    }

    public long getTotalCommands() {
        return totalCommands;
    }

    public void setTotalCommands(long totalCommands) {
        this.totalCommands = totalCommands;
    }

    public long getAppliedCommands() {
        return appliedCommands;
    }

    public void setAppliedCommands(long appliedCommands) {
        this.appliedCommands = appliedCommands;
    }

    public long getDuplicateCommands() {
        return duplicateCommands;
    }

    public void setDuplicateCommands(long duplicateCommands) {
        this.duplicateCommands = duplicateCommands;
    }

    public long getRejectedCommands() {
        return rejectedCommands;
    }

    public void setRejectedCommands(long rejectedCommands) {
        this.rejectedCommands = rejectedCommands;
    }

    public long getConflictCommands() {
        return conflictCommands;
    }

    public void setConflictCommands(long conflictCommands) {
        this.conflictCommands = conflictCommands;
    }

    public long getFailedCommands() {
        return failedCommands;
    }

    public void setFailedCommands(long failedCommands) {
        this.failedCommands = failedCommands;
    }

    public Long getAverageDurationMs() {
        return averageDurationMs;
    }

    public void setAverageDurationMs(Long averageDurationMs) {
        this.averageDurationMs = averageDurationMs;
    }

    public Map<String, Long> getTopConflictCodes() {
        return topConflictCodes;
    }

    public void setTopConflictCodes(Map<String, Long> topConflictCodes) {
        this.topConflictCodes = topConflictCodes;
    }

    public Map<String, Long> getTopErrorCodes() {
        return topErrorCodes;
    }

    public void setTopErrorCodes(Map<String, Long> topErrorCodes) {
        this.topErrorCodes = topErrorCodes;
    }

    public Map<String, Long> getAppVersionBreakdown() {
        return appVersionBreakdown;
    }

    public void setAppVersionBreakdown(Map<String, Long> appVersionBreakdown) {
        this.appVersionBreakdown = appVersionBreakdown;
    }

    public List<DeviceFailureRankItem> getDeviceFailureRanking() {
        return deviceFailureRanking;
    }

    public void setDeviceFailureRanking(List<DeviceFailureRankItem> deviceFailureRanking) {
        this.deviceFailureRanking = deviceFailureRanking;
    }
}

