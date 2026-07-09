package com.restaurante.dto.request;

import com.restaurante.model.enums.PaymentMethodStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class UpdateTenantPaymentMethodRequest {

    @Size(max = 100)
    private String displayName;

    @Size(max = 255)
    private String description;

    private PaymentMethodStatus status;

    private Boolean enabledForQr;
    private Boolean enabledForPos;
    private Boolean enabledForPedido;
    private Boolean enabledForFundoConsumo;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    private Integer sortOrder;

    @Size(max = 80)
    private String iconKey;

    private Map<String, Object> metadata;
}

