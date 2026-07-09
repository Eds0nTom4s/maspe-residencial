package com.restaurante.dto.request;

import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import jakarta.validation.constraints.NotNull;

public class RevisarCaixaOperadorRequest {

    @NotNull
    private CaixaOperadorSessionStatus status;

    private String reviewNotes;

    public CaixaOperadorSessionStatus getStatus() {
        return status;
    }

    public void setStatus(CaixaOperadorSessionStatus status) {
        this.status = status;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}

