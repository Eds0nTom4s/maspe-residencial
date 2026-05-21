package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentConfirmationMode;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodProvider;
import com.restaurante.model.enums.PaymentMethodType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AvailablePaymentMethodResponse {

    private PaymentMethodCode code;
    private String displayName;
    private String description;
    private PaymentMethodType type;
    private PaymentConfirmationMode confirmationMode;
    private PaymentMethodProvider provider;
    private boolean requiresOpenTurno;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String currency;
    private Integer sortOrder;
    private String iconKey;
}

