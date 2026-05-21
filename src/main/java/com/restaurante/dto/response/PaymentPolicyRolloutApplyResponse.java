package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodPolicyRolloutStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class PaymentPolicyRolloutApplyResponse {
    private Long rolloutId;
    private Long templateId;
    private Long unidadeId;
    private PaymentMethodPolicyRolloutStatus status;
    private int totalDevicesTargeted;
    private int totalPoliciesCreated;
    private int totalPoliciesUpdated;
    private int totalPoliciesSkipped;
    private int totalErrors;
    private Instant startedAt;
    private Instant finishedAt;
    private List<DevicePolicyRolloutResultResponse> deviceResults;
}

