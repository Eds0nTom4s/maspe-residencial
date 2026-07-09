package com.restaurante.dto.request;

import com.restaurante.model.enums.SessaoParticipanteRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DeviceSessaoParticipanteRoleChangeRequest {

    @NotNull
    private SessaoParticipanteRole targetRole;

    @NotBlank
    private String reason;

    public SessaoParticipanteRole getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(SessaoParticipanteRole targetRole) {
        this.targetRole = targetRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

