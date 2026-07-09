package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyOverwriteMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutItemAction;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutItemStatus;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutSkippedReason;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PaymentPolicyRolloutItemResponse {
    private Long id;
    private Long rolloutId;
    private Long deviceId;
    private PaymentMethodCode paymentMethodCode;
    private PaymentMethodPolicyRolloutItemAction plannedAction;
    private PaymentMethodPolicyRolloutItemStatus status;
    private PaymentMethodPolicyOverwriteMode overwriteMode;
    private boolean manualOverrideDetected;
    private PaymentMethodPolicyRolloutSkippedReason skippedReason;
    private String errorCode;
    private String errorMessage;
    private int attempts;
    private boolean terminalFailure;
    private Instant nextRetryAt;
    private Instant lastAttemptAt;
    private Instant lastLockedAt;
    private String lockedBy;
    private int staleRecoveryCount;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant updatedAt;
}
