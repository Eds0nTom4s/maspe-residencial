package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;

public class AbrirCaixaOperadorRequest {

    @NotNull
    private Long operadorUserId;

    private Long turnoId;

    private String notes;

    public Long getOperadorUserId() {
        return operadorUserId;
    }

    public void setOperadorUserId(Long operadorUserId) {
        this.operadorUserId = operadorUserId;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

