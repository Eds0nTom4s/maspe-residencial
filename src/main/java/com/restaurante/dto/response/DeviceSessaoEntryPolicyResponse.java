package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoParticipantEntryPolicy;

import java.time.Instant;

public class DeviceSessaoEntryPolicyResponse {

    private Long sessaoConsumoId;
    private SessaoParticipantEntryPolicy policy;
    private Instant updatedAt;

    public Long getSessaoConsumoId() {
        return sessaoConsumoId;
    }

    public void setSessaoConsumoId(Long sessaoConsumoId) {
        this.sessaoConsumoId = sessaoConsumoId;
    }

    public SessaoParticipantEntryPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(SessaoParticipantEntryPolicy policy) {
        this.policy = policy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

