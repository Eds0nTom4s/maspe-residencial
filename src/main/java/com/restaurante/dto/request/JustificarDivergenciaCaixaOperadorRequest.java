package com.restaurante.dto.request;

import com.restaurante.model.enums.CaixaOperadorDivergenceReasonCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class JustificarDivergenciaCaixaOperadorRequest {
    @NotNull
    private CaixaOperadorDivergenceReasonCategory reasonCategory;

    @NotBlank
    private String description;

    public CaixaOperadorDivergenceReasonCategory getReasonCategory() {
        return reasonCategory;
    }

    public void setReasonCategory(CaixaOperadorDivergenceReasonCategory reasonCategory) {
        this.reasonCategory = reasonCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

