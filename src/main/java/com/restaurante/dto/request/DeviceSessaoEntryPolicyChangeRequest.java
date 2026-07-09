package com.restaurante.dto.request;

import com.restaurante.model.enums.SessaoParticipantEntryPolicy;
import jakarta.validation.constraints.NotNull;

public class DeviceSessaoEntryPolicyChangeRequest {

    @NotNull
    private SessaoParticipantEntryPolicy policy;

    public SessaoParticipantEntryPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(SessaoParticipantEntryPolicy policy) {
        this.policy = policy;
    }
}

