package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentConfirmationMode;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodProvider;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.PaymentMethodType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TenantPaymentMethodResponse {

    private PaymentMethodCode code;
    private String displayName;
    private String description;
    private PaymentMethodStatus status;
    private PaymentMethodType type;
    private PaymentConfirmationMode confirmationMode;
    private PaymentMethodProvider provider;

    private boolean enabledForQr;
    private boolean enabledForPos;
    private boolean enabledForPedido;
    private boolean enabledForFundoConsumo;

    private boolean requiresOpenTurno;
    private boolean requiresGateway;
    private boolean requiresManualConfirmation;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String currency;
    private Integer sortOrder;
    private String iconKey;
    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

