package com.restaurante.dto.request;

import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class UpdateUnidadePaymentMethodPolicyRequest {

    private PaymentMethodPolicyStatus status;
    private Boolean enabledForQr;
    private Boolean enabledForPos;
    private Boolean enabledForPedido;
    private Boolean enabledForFundoConsumo;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Boolean inheritFromTenant;

    @Size(max = 255)
    private String overrideReason;

    private Map<String, Object> metadata;
}

