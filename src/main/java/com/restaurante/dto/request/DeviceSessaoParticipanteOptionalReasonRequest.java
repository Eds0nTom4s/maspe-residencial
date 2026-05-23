package com.restaurante.dto.request;

import jakarta.validation.constraints.Size;

public class DeviceSessaoParticipanteOptionalReasonRequest {

    @Size(max = 255)
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

