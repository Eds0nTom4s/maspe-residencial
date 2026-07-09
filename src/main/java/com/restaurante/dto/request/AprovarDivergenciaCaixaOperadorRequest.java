package com.restaurante.dto.request;

import com.restaurante.model.enums.CaixaOperadorAdjustmentDirection;
import com.restaurante.model.enums.CaixaOperadorAdjustmentType;

public class AprovarDivergenciaCaixaOperadorRequest {
    private String reviewNotes;
    private CaixaOperadorAdjustmentType adjustmentType;
    private CaixaOperadorAdjustmentDirection direction;
    private String evidenceReference;

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public CaixaOperadorAdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(CaixaOperadorAdjustmentType adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public CaixaOperadorAdjustmentDirection getDirection() {
        return direction;
    }

    public void setDirection(CaixaOperadorAdjustmentDirection direction) {
        this.direction = direction;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public void setEvidenceReference(String evidenceReference) {
        this.evidenceReference = evidenceReference;
    }
}

