package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;

import java.time.Instant;

public class DeviceSessaoParticipanteManagementResponse {

    private Long participanteId;
    private Long clienteConsumoId;
    private SessaoParticipanteRole role;
    private SessaoParticipanteStatus status;
    private Instant joinedAt;
    private Instant removedAt;
    private Instant blockedAt;

    public Long getParticipanteId() {
        return participanteId;
    }

    public void setParticipanteId(Long participanteId) {
        this.participanteId = participanteId;
    }

    public Long getClienteConsumoId() {
        return clienteConsumoId;
    }

    public void setClienteConsumoId(Long clienteConsumoId) {
        this.clienteConsumoId = clienteConsumoId;
    }

    public SessaoParticipanteRole getRole() {
        return role;
    }

    public void setRole(SessaoParticipanteRole role) {
        this.role = role;
    }

    public SessaoParticipanteStatus getStatus() {
        return status;
    }

    public void setStatus(SessaoParticipanteStatus status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(Instant blockedAt) {
        this.blockedAt = blockedAt;
    }
}

