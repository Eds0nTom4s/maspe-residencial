package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
public class PaymentMethodPolicyResponse {

    private Long id;
    private PaymentMethodCode code;
    private PaymentMethodPolicyStatus status;
    private Boolean inherit;
    private Boolean enabledForQr;
    private Boolean enabledForPos;
    private Boolean enabledForPedido;
    private Boolean enabledForFundoConsumo;
    private Boolean canConfirmManual;
    private Boolean canStartGateway;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String overrideReason;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
}

