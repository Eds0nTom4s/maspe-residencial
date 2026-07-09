package com.restaurante.dto.request;

import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class PaymentPolicyTemplateItemRequest {

    @NotNull
    private PaymentMethodCode paymentMethodCode;

    @NotNull
    private PaymentMethodPolicyStatus policyStatus;

    private Boolean enabledForPos;
    private Boolean enabledForPedido;
    private Boolean enabledForFundoConsumo;
    private Boolean canConfirmManual;
    private Boolean canStartGateway;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    @Size(max = 255)
    private String overrideReason;

    private Map<String, Object> metadata;
}

