package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.model.enums.CaixaOperadorAdjustmentDirection;
import com.restaurante.model.enums.CaixaOperadorAdjustmentStatus;
import com.restaurante.model.enums.CaixaOperadorAdjustmentType;
import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OperatorCashAdjustmentEvidenceItemDTO {
    private Long adjustmentId;
    private Long divergenceId;
    private Long caixaId;
    private CaixaOperadorAdjustmentStatus status;
    private CaixaOperadorAdjustmentType adjustmentType;
    private CaixaOperadorDivergencePaymentMethod paymentMethod;
    private BigDecimal amount;
    private CaixaOperadorAdjustmentDirection direction;
    private LocalDateTime approvedAt;
    private String adjustmentHash;
}

