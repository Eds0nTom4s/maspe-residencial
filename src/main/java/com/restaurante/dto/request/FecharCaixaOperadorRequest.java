package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class FecharCaixaOperadorRequest {

    @NotNull
    private BigDecimal declaredCashAmount;

    @NotNull
    private BigDecimal declaredTpaAmount;

    private Long closedByUserId;

    private String closeReason;

    private String notes;

    public BigDecimal getDeclaredCashAmount() {
        return declaredCashAmount;
    }

    public void setDeclaredCashAmount(BigDecimal declaredCashAmount) {
        this.declaredCashAmount = declaredCashAmount;
    }

    public BigDecimal getDeclaredTpaAmount() {
        return declaredTpaAmount;
    }

    public void setDeclaredTpaAmount(BigDecimal declaredTpaAmount) {
        this.declaredTpaAmount = declaredTpaAmount;
    }

    public Long getClosedByUserId() {
        return closedByUserId;
    }

    public void setClosedByUserId(Long closedByUserId) {
        this.closedByUserId = closedByUserId;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

