package com.restaurante.financeiro.polling;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GatewayPaymentStatusResponse {
    private String externalReference;
    private String gatewayChargeId;
    private GatewayPaymentStatus status;
    private Long amountCents;
    private String rawStatus;
    private String rawPayload;
    private LocalDateTime paidAt;
    private String errorCode;
    private String errorMessage;
}

