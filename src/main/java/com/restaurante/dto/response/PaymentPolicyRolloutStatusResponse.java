package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodPolicyOverwriteMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutExecutionMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PaymentPolicyRolloutStatusResponse {
    private Long rolloutId;
    private Long templateId;
    private Long unidadeId;
    private PaymentMethodPolicyRolloutStatus status;
    private PaymentMethodPolicyRolloutExecutionMode executionMode;
    private PaymentMethodPolicyRolloutMode rolloutMode;
    private PaymentMethodPolicyOverwriteMode overwriteMode;

    private int totalItems;
    private int processedItems;
    private int pendingItems;
    private int succeededItems;
    private int skippedItems;
    private int failedItems;
    private int retryCount;
    private Integer progressPercent;

    private Instant requestedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant lastProgressAt;
    private String lastError;
}

