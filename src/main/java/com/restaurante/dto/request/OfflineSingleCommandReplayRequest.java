package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;

public class OfflineSingleCommandReplayRequest {

    @NotBlank
    private String reason;

    private Boolean force = Boolean.FALSE;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }
}

