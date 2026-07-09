package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;
import com.restaurante.model.enums.CaixaOperadorDivergenceReasonCategory;
import com.restaurante.model.enums.CaixaOperadorDivergenceSeverity;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OperatorCashDivergenceEvidenceItemDTO {
    private Long divergenceId;
    private Long caixaId;
    private CaixaOperadorDivergenceStatus status;
    private CaixaOperadorDivergenceType type;
    private CaixaOperadorDivergenceSeverity severity;
    private CaixaOperadorDivergencePaymentMethod paymentMethod;
    private BigDecimal differenceAmount;
    private BigDecimal absoluteDifferenceAmount;
    private CaixaOperadorDivergenceReasonCategory reasonCategory;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String divergenceHash;
}

