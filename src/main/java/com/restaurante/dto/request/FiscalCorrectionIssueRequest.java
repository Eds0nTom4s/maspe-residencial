package com.restaurante.dto.request;

import com.restaurante.model.enums.FiscalCorrectionLineMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FiscalCorrectionIssueRequest {
    @NotNull
    private BigDecimal amount;
    @NotNull
    private String reason;
    private FiscalCorrectionLineMode lineMode = FiscalCorrectionLineMode.SINGLE_ADJUSTMENT_LINE;
    private Long originalFiscalDocumentId;
}

