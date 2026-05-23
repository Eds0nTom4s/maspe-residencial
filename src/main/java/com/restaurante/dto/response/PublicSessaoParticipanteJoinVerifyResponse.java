package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;

import java.time.Instant;

public class PublicSessaoParticipanteJoinVerifyResponse {

    private Long participanteId;
    private Long clienteConsumoId;
    private Long sessaoConsumoId;
    private SessaoParticipanteRole role;
    private SessaoParticipanteStatus status;
    private String telefoneMascarado;
    private Instant joinedAt;

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

    public Long getSessaoConsumoId() {
        return sessaoConsumoId;
    }

    public void setSessaoConsumoId(Long sessaoConsumoId) {
        this.sessaoConsumoId = sessaoConsumoId;
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

    public String getTelefoneMascarado() {
        return telefoneMascarado;
    }

    public void setTelefoneMascarado(String telefoneMascarado) {
        this.telefoneMascarado = telefoneMascarado;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}

