package com.restaurante.dto.response;

import java.util.List;

public class OfflineCommandReplayPreviewResponse {

    private String serverSyncId;
    private int totalCommandsAnalyzed;
    private int eligibleCount;
    private int notEligibleCount;
    private int requiresReviewCount;
    private List<OfflineCommandReplayEligibilityResponse> items;

    public String getServerSyncId() {
        return serverSyncId;
    }

    public void setServerSyncId(String serverSyncId) {
        this.serverSyncId = serverSyncId;
    }

    public int getTotalCommandsAnalyzed() {
        return totalCommandsAnalyzed;
    }

    public void setTotalCommandsAnalyzed(int totalCommandsAnalyzed) {
        this.totalCommandsAnalyzed = totalCommandsAnalyzed;
    }

    public int getEligibleCount() {
        return eligibleCount;
    }

    public void setEligibleCount(int eligibleCount) {
        this.eligibleCount = eligibleCount;
    }

    public int getNotEligibleCount() {
        return notEligibleCount;
    }

    public void setNotEligibleCount(int notEligibleCount) {
        this.notEligibleCount = notEligibleCount;
    }

    public int getRequiresReviewCount() {
        return requiresReviewCount;
    }

    public void setRequiresReviewCount(int requiresReviewCount) {
        this.requiresReviewCount = requiresReviewCount;
    }

    public List<OfflineCommandReplayEligibilityResponse> getItems() {
        return items;
    }

    public void setItems(List<OfflineCommandReplayEligibilityResponse> items) {
        this.items = items;
    }
}

