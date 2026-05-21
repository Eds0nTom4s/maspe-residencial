package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodPolicyRolloutStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PaymentPolicyRolloutSubmitResponse {
    private Long rolloutId;
    private PaymentMethodPolicyRolloutStatus status;
    private int totalItems;
    private int totalDevicesTargeted;
    private Instant requestedAt;
}

