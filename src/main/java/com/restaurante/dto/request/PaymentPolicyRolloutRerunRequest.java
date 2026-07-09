package com.restaurante.dto.request;

import com.restaurante.model.enums.PaymentMethodPolicyRolloutItemStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class PaymentPolicyRolloutRerunRequest {
    private Set<PaymentMethodPolicyRolloutItemStatus> onlyStatuses;
}

