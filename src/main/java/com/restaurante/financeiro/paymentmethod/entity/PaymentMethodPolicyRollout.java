package com.restaurante.financeiro.paymentmethod.entity;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.PaymentMethodPolicyOverwriteMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutStatus;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutExecutionMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payment_method_policy_rollouts")
@Getter
@Setter
public class PaymentMethodPolicyRollout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private PaymentMethodPolicyTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false, updatable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_device_type", length = 50)
    private OperationalDeviceType targetDeviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rollout_mode", nullable = false, length = 50)
    private PaymentMethodPolicyRolloutMode rolloutMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "overwrite_mode", nullable = false, length = 50)
    private PaymentMethodPolicyOverwriteMode overwriteMode;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 30)
    private PaymentMethodPolicyRolloutExecutionMode executionMode = PaymentMethodPolicyRolloutExecutionMode.SYNC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentMethodPolicyRolloutStatus status;

    @Column(name = "total_devices_targeted", nullable = false)
    private int totalDevicesTargeted;

    @Column(name = "total_policies_created", nullable = false)
    private int totalPoliciesCreated;

    @Column(name = "total_policies_updated", nullable = false)
    private int totalPoliciesUpdated;

    @Column(name = "total_policies_skipped", nullable = false)
    private int totalPoliciesSkipped;

    @Column(name = "total_errors", nullable = false)
    private int totalErrors;

    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "last_progress_at")
    private Instant lastProgressAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "created_by", updatable = false)
    private Long createdBy; // legado

    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "processed_items", nullable = false)
    private int processedItems;

    @Column(name = "pending_items", nullable = false)
    private int pendingItems;

    @Column(name = "succeeded_items", nullable = false)
    private int succeededItems;

    @Column(name = "skipped_items", nullable = false)
    private int skippedItems;

    @Column(name = "failed_items", nullable = false)
    private int failedItems;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested = false;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(name = "cancel_requested_by")
    private Long cancelRequestedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "last_progress_event_at")
    private Instant lastProgressEventAt;

    @Column(name = "last_progress_event_percent")
    private Integer lastProgressEventPercent;

    @Column(name = "stale_recovery_count", nullable = false)
    private int staleRecoveryCount;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
    }
}
