package com.restaurante.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelPaymentPolicyRolloutRequest {
    @Size(max = 255)
    private String reason;
}

